using Quartz;
using StructureMap;
using System;
using Topshelf;
using Topshelf.Quartz.StructureMap;
using Topshelf.StructureMap;

namespace OS2faktorADSync
{
    class Program
    {
        static int hour = 2;
        static int minute = 0;

        static void Main(string[] args)
        {
            // Setting Environment directory to store logs to relative paths correctly
            Environment.CurrentDirectory = AppDomain.CurrentDomain.BaseDirectory;
            System.Net.ServicePointManager.ServerCertificateValidationCallback = (senderX, certificate, chain, sslPolicyErrors) => { return true; };
            InitCronSchedule();

            HostFactory.Run(h =>
            {
                h.UseStructureMap(new Container(c =>
                {
                    c.AddRegistry(new DefaultRegistry());
                }));
                h.SetDisplayName("OS2faktor Coredata Sync");
                h.SetServiceName("OS2faktor Coredata Sync");
                h.Service<Service>(s =>
                {
                    s.ConstructUsingStructureMap();
                    s.UseQuartzStructureMap();
                    s.ScheduleQuartzJob(q =>
                        q.WithJob(() => JobBuilder.Create<SynchronizeJob>().Build())
                            .AddTrigger(() => TriggerBuilder.Create()
                            .WithSimpleSchedule(b => b.WithIntervalInSeconds(10)
                            .RepeatForever())
                        .Build()));
                    s.ScheduleQuartzJob(q =>
                        q.WithJob(() => JobBuilder.Create<ResetDirSyncJob>().Build())
                            .AddTrigger(() => TriggerBuilder.Create()
                            .WithCronSchedule("0 " + minute + " " + hour + " ? * * *")
                        .Build()));
                });
            });
        }

        private static void InitCronSchedule()
        {
            // use password as random seed
            string tmp = Settings.GetStringValue("Backend.Password");
            if (!string.IsNullOrEmpty(tmp) && tmp.Length >= 2)
            {
                int x = (int)tmp[0];
                int y = (int)tmp[1];

                hour = (x % 2 == 0) ? 5 : 6;
                minute = (y % 30);

                if (hour == 5) { minute += 30; }
            }
        }
    }
}
