namespace DragonRacing.Shared;

public static class BannerHelper
{
    public static void PrintPortalBanner()
    {
        Console.ForegroundColor = ConsoleColor.DarkRed;
        Console.WriteLine(@"
    ____                                ____            _
   / __ \_________ _____ _____  ____   / __ \____  _____/ /_____ _/ /
  / / / / ___/ __ `/ __ `/ __ \/ __ \ / /_/ / __ \/ ___/ __/ __ `/ /
 / /_/ / /  / /_/ / /_/ / /_/ / / / // ____/ /_/ / /  / /_/ /_/ / /
/_____/_/   \__,_/\__, /\____/_/ /_//_/    \____/_/   \__/\__,_/_/
                 /____/
        ");
        Console.ForegroundColor = ConsoleColor.DarkYellow;
        Console.WriteLine("    === Dragon Racing League - Race Portal ===");
        Console.WriteLine("    >>> Kestrel listening on port 5000 <<<");
        Console.ResetColor();
        Console.WriteLine();
    }

    public static void PrintBettingApiBanner()
    {
        Console.ForegroundColor = ConsoleColor.Green;
        Console.WriteLine(@"
    ____       __  __  _                ___    ____  ____
   / __ )___  / /_/ /_(_)___  ____ _  /   |  / __ \/  _/
  / __  / _ \/ __/ __/ / __ \/ __ `/ / /| | / /_/ // /
 / /_/ /  __/ /_/ /_/ / / / / /_/ / / ___ |/ ____// /
/_____/\___/\__/\__/_/_/ /_/\__, / /_/  |_/_/   /___/
                            /____/
        ");
        Console.ForegroundColor = ConsoleColor.DarkYellow;
        Console.WriteLine("    === Dragon Racing League - Betting API ===");
        Console.WriteLine("    >>> Kestrel listening on port 5001 <<<");
        Console.ResetColor();
        Console.WriteLine();
    }

    public static void PrintRaceSimulatorBanner()
    {
        Console.ForegroundColor = ConsoleColor.Cyan;
        Console.WriteLine(@"
    ____                  _____ _                 __      __
   / __ \____ _________  / ___/(_)___ ___  __  __/ /___ _/ /_____  _____
  / /_/ / __ `/ ___/ _ \ \__ \/ / __ `__ \/ / / / / __ `/ __/ __ \/ ___/
 / _, _/ /_/ / /__/  __/___/ / / / / / / / /_/ / / /_/ / /_/ /_/ / /
/_/ |_|\__,_/\___/\___//____/_/_/ /_/ /_/\__,_/_/\__,_/\__/\____/_/
        ");
        Console.ForegroundColor = ConsoleColor.DarkYellow;
        Console.WriteLine("    === Dragon Racing League - Race Simulator ===");
        Console.WriteLine("    >>> Polling for pending races every 3 seconds <<<");
        Console.ResetColor();
        Console.WriteLine();
    }

    public static void PrintPayoutProcessorBanner()
    {
        Console.ForegroundColor = ConsoleColor.Magenta;
        Console.WriteLine(@"
    ____                          __     ____
   / __ \____ ___  ______  __  __/ /_   / __ \_________  ________  ______________  _____
  / /_/ / __ `/ / / / __ \/ / / / __/  / /_/ / ___/ __ \/ ___/ _ \/ ___/ ___/ __ \/ ___/
 / ____/ /_/ / /_/ / /_/ / /_/ / /_   / ____/ /  / /_/ / /__/  __(__  |__  ) /_/ / /
/_/    \__,_/\__, /\____/\__,_/\__/  /_/   /_/   \____/\___/\___/____/____/\____/_/
            /____/
        ");
        Console.ForegroundColor = ConsoleColor.DarkYellow;
        Console.WriteLine("    === Dragon Racing League - Payout Processor ===");
        Console.WriteLine("    >>> Polling for pending payouts every 5 seconds <<<");
        Console.ResetColor();
        Console.WriteLine();
    }
}
