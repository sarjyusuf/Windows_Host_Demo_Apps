using Microsoft.EntityFrameworkCore;
using EcommercePlatform.Shared.Models;

namespace EcommercePlatform.WebStorefront.Data;

public class StorefrontDbContext : DbContext
{
    public StorefrontDbContext(DbContextOptions<StorefrontDbContext> options)
        : base(options)
    {
    }

    public DbSet<Product> Products => Set<Product>();

    protected override void OnModelCreating(ModelBuilder modelBuilder)
    {
        base.OnModelCreating(modelBuilder);

        modelBuilder.Entity<Product>(entity =>
        {
            entity.HasKey(p => p.Id);
            entity.Property(p => p.Name).IsRequired().HasMaxLength(200);
            entity.Property(p => p.Description).HasMaxLength(1000);
            entity.Property(p => p.Category).IsRequired().HasMaxLength(100);
            entity.Property(p => p.Price).HasColumnType("decimal(18,2)");
            entity.Property(p => p.Sku).IsRequired().HasMaxLength(50);

            entity.HasData(
                new Product
                {
                    Id = 1,
                    Name = "Wireless Bluetooth Headphones",
                    Description = "Premium noise-cancelling over-ear headphones with 30-hour battery life.",
                    Category = "Electronics",
                    Price = 89.99m,
                    Sku = "ELEC-WBH-001"
                },
                new Product
                {
                    Id = 2,
                    Name = "USB-C Laptop Charger 65W",
                    Description = "Universal fast-charging USB-C power adapter compatible with most laptops.",
                    Category = "Electronics",
                    Price = 34.99m,
                    Sku = "ELEC-ULC-002"
                },
                new Product
                {
                    Id = 3,
                    Name = "Mechanical Gaming Keyboard",
                    Description = "RGB backlit mechanical keyboard with Cherry MX Blue switches.",
                    Category = "Electronics",
                    Price = 129.99m,
                    Sku = "ELEC-MGK-003"
                },
                new Product
                {
                    Id = 4,
                    Name = "Men's Classic Fit T-Shirt",
                    Description = "100% cotton crew-neck t-shirt available in multiple colors.",
                    Category = "Clothing",
                    Price = 19.99m,
                    Sku = "CLTH-MCT-004"
                },
                new Product
                {
                    Id = 5,
                    Name = "Women's Running Shoes",
                    Description = "Lightweight breathable running shoes with cushioned sole.",
                    Category = "Clothing",
                    Price = 74.99m,
                    Sku = "CLTH-WRS-005"
                },
                new Product
                {
                    Id = 6,
                    Name = "Stainless Steel Water Bottle",
                    Description = "Double-walled insulated 32oz water bottle keeps drinks cold for 24 hours.",
                    Category = "Home & Kitchen",
                    Price = 24.99m,
                    Sku = "HOME-SSW-006"
                },
                new Product
                {
                    Id = 7,
                    Name = "Portable Bluetooth Speaker",
                    Description = "Waterproof portable speaker with 12-hour battery and deep bass.",
                    Category = "Electronics",
                    Price = 49.99m,
                    Sku = "ELEC-PBS-007"
                },
                new Product
                {
                    Id = 8,
                    Name = "Yoga Mat Premium",
                    Description = "Non-slip 6mm thick yoga mat with carrying strap.",
                    Category = "Sports & Fitness",
                    Price = 29.99m,
                    Sku = "SPRT-YMP-008"
                },
                new Product
                {
                    Id = 9,
                    Name = "Leather Wallet Bifold",
                    Description = "Genuine leather bifold wallet with RFID blocking technology.",
                    Category = "Accessories",
                    Price = 39.99m,
                    Sku = "ACCS-LWB-009"
                },
                new Product
                {
                    Id = 10,
                    Name = "LED Desk Lamp",
                    Description = "Adjustable LED desk lamp with 5 brightness levels and USB charging port.",
                    Category = "Home & Kitchen",
                    Price = 44.99m,
                    Sku = "HOME-LDL-010"
                }
            );
        });
    }
}
