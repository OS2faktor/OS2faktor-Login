using System;
using System.Net;
using System.Threading;
using Serilog;
using StructureMap;

namespace OS2faktor
{
    class Program
    {
        static void Main(string[] args)
        {
            ServicePointManager.SecurityProtocol = SecurityProtocolType.Tls12;

            Environment.CurrentDirectory = AppDomain.CurrentDomain.BaseDirectory;

            var container = Container.For<DefaultRegistry>();
            var app = container.GetInstance<Application>();
            app.Run();
            Thread.Sleep(20000);

            Console.WriteLine("Done!");
        }
    }
}
