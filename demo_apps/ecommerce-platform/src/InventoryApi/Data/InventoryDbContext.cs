using Microsoft.EntityFrameworkCore;
using EcommercePlatform.Shared.Models;

namespace EcommercePlatform.InventoryApi.Data;

public class InventoryDbContext : DbContext
{
    public InventoryDbContext(DbContextOptions<InventoryDbContext> options)
        : base(options)
    {
    }

    public DbSet<InventoryItem> InventoryItems => Set<InventoryItem>();

    protected override void OnModelCreating(ModelBuilder modelBuilder)
    {
        base.OnModelCreating(modelBuilder);

        modelBuilder.Entity<InventoryItem>(entity =>
        {
            entity.HasKey(i => i.Id);
            entity.Property(i => i.Sku).IsRequired().HasMaxLength(50);
            entity.Property(i => i.WarehouseLocation).IsRequired().HasMaxLength(20);
            entity.HasIndex(i => i.ProductId).IsUnique();
            entity.HasIndex(i => i.Sku).IsUnique();

            // Ignore the computed property so EF Core does not try to map it to a column
            entity.Ignore(i => i.QuantityAvailable);

            entity.HasData(
                new InventoryItem
                {
                    Id = 1,
                    ProductId = 1,
                    Sku = "ELEC-WBH-001",
                    QuantityOnHand = 150,
                    QuantityReserved = 0,
                    WarehouseLocation = "A-1-01",
                    LastUpdated = new DateTime(2025, 1, 15, 0, 0, 0, DateTimeKind.Utc)
                },
                new InventoryItem
                {
                    Id = 2,
                    ProductId = 2,
                    Sku = "ELEC-ULC-002",
                    QuantityOnHand = 200,
                    QuantityReserved = 0,
                    WarehouseLocation = "A-1-02",
                    LastUpdated = new DateTime(2025, 1, 15, 0, 0, 0, DateTimeKind.Utc)
                },
                new InventoryItem
                {
                    Id = 3,
                    ProductId = 3,
                    Sku = "ELEC-MGK-003",
                    QuantityOnHand = 75,
                    QuantityReserved = 0,
                    WarehouseLocation = "A-2-05",
                    LastUpdated = new DateTime(2025, 1, 15, 0, 0, 0, DateTimeKind.Utc)
                },
                new InventoryItem
                {
                    Id = 4,
                    ProductId = 4,
                    Sku = "CLTH-MCT-004",
                    QuantityOnHand = 500,
                    QuantityReserved = 0,
                    WarehouseLocation = "B-1-01",
                    LastUpdated = new DateTime(2025, 1, 15, 0, 0, 0, DateTimeKind.Utc)
                },
                new InventoryItem
                {
                    Id = 5,
                    ProductId = 5,
                    Sku = "CLTH-WRS-005",
                    QuantityOnHand = 120,
                    QuantityReserved = 0,
                    WarehouseLocation = "B-2-03",
                    LastUpdated = new DateTime(2025, 1, 15, 0, 0, 0, DateTimeKind.Utc)
                },
                new InventoryItem
                {
                    Id = 6,
                    ProductId = 6,
                    Sku = "HOME-SSW-006",
                    QuantityOnHand = 180,
                    QuantityReserved = 0,
                    WarehouseLocation = "C-1-08",
                    LastUpdated = new DateTime(2025, 1, 15, 0, 0, 0, DateTimeKind.Utc)
                },
                new InventoryItem
                {
                    Id = 7,
                    ProductId = 7,
                    Sku = "ELEC-PBS-007",
                    QuantityOnHand = 90,
                    QuantityReserved = 0,
                    WarehouseLocation = "A-3-12",
                    LastUpdated = new DateTime(2025, 1, 15, 0, 0, 0, DateTimeKind.Utc)
                },
                new InventoryItem
                {
                    Id = 8,
                    ProductId = 8,
                    Sku = "SPRT-YMP-008",
                    QuantityOnHand = 60,
                    QuantityReserved = 0,
                    WarehouseLocation = "D-1-02",
                    LastUpdated = new DateTime(2025, 1, 15, 0, 0, 0, DateTimeKind.Utc)
                },
                new InventoryItem
                {
                    Id = 9,
                    ProductId = 9,
                    Sku = "ACCS-LWB-009",
                    QuantityOnHand = 200,
                    QuantityReserved = 0,
                    WarehouseLocation = "B-3-07",
                    LastUpdated = new DateTime(2025, 1, 15, 0, 0, 0, DateTimeKind.Utc)
                },
                new InventoryItem
                {
                    Id = 10,
                    ProductId = 10,
                    Sku = "HOME-LDL-010",
                    QuantityOnHand = 110,
                    QuantityReserved = 0,
                    WarehouseLocation = "C-2-04",
                    LastUpdated = new DateTime(2025, 1, 15, 0, 0, 0, DateTimeKind.Utc)
                }
            );
        });
    }
}
