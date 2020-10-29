using System;
using Quartz;
using StructureMap;
using Topshelf;
using Topshelf.Quartz.StructureMap;
using Topshelf.StructureMap;

namespace OS2faktor_Password_Agent
{
    class Program
    {
        static void Main(string[] args)
        {
            Environment.CurrentDirectory = AppDomain.CurrentDomain.BaseDirectory;

            HostFactory.Run(configure =>
            {
                configure.UseStructureMap(new Container(c =>
                {
                    c.AddRegistry(new DefaultRegistry());
                }));

                configure.Service<Service>(service =>
                {
                    service.ConstructUsingStructureMap();
                    service.UseQuartzStructureMap();

                    service.ScheduleQuartzJob(q =>
                        q.WithJob(() =>
                            JobBuilder.Create<ReconnectJob>().Build())
                            .AddTrigger(() => TriggerBuilder.Create()
                                                            .WithSimpleSchedule(b => b.WithIntervalInSeconds(30).RepeatForever())
                                                            .Build())
                    );
                });

                configure.RunAsLocalSystem();
                configure.SetServiceName("OS2faktor Password Agent");
                configure.SetDisplayName("OS2faktor Password Agent");
                configure.SetDescription("Bridge between AD and OS2faktor for password validation and synchronization");
            });
        }
    }
}
