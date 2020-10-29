using Serilog;
using Topshelf;

namespace OS2faktor_Password_Agent
{
    class Service : ServiceControl
    {
        public ILogger Logger { get; set; }

        public WSCommunication webSockets { get; set; }

        public bool Start(HostControl hostControl)
        {
            Logger.Information("Starting service");
            webSockets.Init();

            return true;
        }

        public bool Stop(HostControl hostControl)
        {
            Logger.Information("Stopping service");
            webSockets.Disconnect();

            return true;
        }
    }
}
