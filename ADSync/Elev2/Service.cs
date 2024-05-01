using Serilog;
using Topshelf;

namespace OS2faktorADSync
{
    internal class Service : ServiceControl
    {
        public ILogger Logger { get; set; }

        public bool Start(HostControl hostControl)
        {
            Logger.Information("OS2faktor CoreData Service starting");
            return true;
        }

        public bool Stop(HostControl hostControl)
        {
            Logger.Information("OS2faktor CoreData Service stopping");
            return true;
        }
    }
}
