using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using EcommercePlatform.Shared.Models;
using EcommercePlatform.WebStorefront.Data;

namespace EcommercePlatform.WebStorefront.Controllers;

[ApiController]
[Route("api/[controller]")]
public class ProductsController : ControllerBase
{
    private readonly StorefrontDbContext _db;
    private readonly ILogger<ProductsController> _logger;

    public ProductsController(StorefrontDbContext db, ILogger<ProductsController> logger)
    {
        _db = db;
        _logger = logger;
    }

    /// <summary>
    /// GET /api/products - List all products.
    /// </summary>
    [HttpGet]
    public async Task<ActionResult<List<Product>>> GetAll()
    {
        _logger.LogInformation("Listing all products");
        var products = await _db.Products.AsNoTracking().ToListAsync();
        return Ok(products);
    }

    /// <summary>
    /// GET /api/products/{id} - Get a product by its ID.
    /// </summary>
    [HttpGet("{id:int}")]
    public async Task<ActionResult<Product>> GetById(int id)
    {
        _logger.LogInformation("Getting product {ProductId}", id);
        var product = await _db.Products.AsNoTracking().FirstOrDefaultAsync(p => p.Id == id);
        if (product is null)
        {
            _logger.LogWarning("Product {ProductId} not found", id);
            return NotFound(new { message = $"Product with ID {id} not found." });
        }

        return Ok(product);
    }

    /// <summary>
    /// GET /api/products/search?q=term - Search products by name or description.
    /// </summary>
    [HttpGet("search")]
    public async Task<ActionResult<List<Product>>> Search([FromQuery] string q)
    {
        if (string.IsNullOrWhiteSpace(q))
        {
            return BadRequest(new { message = "Query parameter 'q' is required." });
        }

        _logger.LogInformation("Searching products with query: {Query}", q);
        var term = q.ToLower();
        var products = await _db.Products
            .AsNoTracking()
            .Where(p => p.Name.ToLower().Contains(term) || p.Description.ToLower().Contains(term))
            .ToListAsync();

        _logger.LogInformation("Found {Count} products matching '{Query}'", products.Count, q);
        return Ok(products);
    }
}
