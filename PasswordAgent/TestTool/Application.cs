using Serilog;

namespace OS2faktor
{
    class Application
    {
        public ILogger Logger { get; set; }

        public WSCommunication WebSockets { get; set; }

        public void Run()
        {
            WebSockets.Init(WebSocketSharp.LogLevel.Debug);
        }
    }
}
