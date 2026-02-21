using EcommercePlatform.Shared.Models;
using Microsoft.EntityFrameworkCore;

namespace EcommercePlatform.NotificationWorker.Data;

public class NotificationDbContext : DbContext
{
    public DbSet<Notification> Notifications => Set<Notification>();

    private readonly string _databasePath;

    public NotificationDbContext(DbContextOptions<NotificationDbContext> options)
        : base(options)
    {
    }

    public NotificationDbContext(string databasePath)
    {
        _databasePath = databasePath;
    }

    protected override void OnConfiguring(DbContextOptionsBuilder optionsBuilder)
    {
        if (!optionsBuilder.IsConfigured)
        {
            optionsBuilder.UseSqlite($"Data Source={_databasePath}");
        }
    }

    protected override void OnModelCreating(ModelBuilder modelBuilder)
    {
        modelBuilder.Entity<Notification>(entity =>
        {
            entity.HasKey(n => n.Id);
            entity.Property(n => n.OrderNumber).IsRequired().HasMaxLength(50);
            entity.Property(n => n.CustomerEmail).IsRequired().HasMaxLength(255);
            entity.Property(n => n.CustomerName).IsRequired().HasMaxLength(255);
            entity.Property(n => n.Subject).IsRequired().HasMaxLength(500);
            entity.Property(n => n.Body).IsRequired();
            entity.Property(n => n.Type).HasConversion<string>().HasMaxLength(50);
            entity.Property(n => n.Status).HasConversion<string>().HasMaxLength(50);
            entity.HasIndex(n => n.Status);
            entity.HasIndex(n => n.OrderNumber);
        });
    }
}
