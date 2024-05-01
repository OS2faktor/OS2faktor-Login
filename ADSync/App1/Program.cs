using Quartz;
using StructureMap;
using System;
using System.Net;
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
            ServicePointManager.SecurityProtocol = SecurityProtocolType.Tls12;

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

                    string kombitCron = Settings.GetStringValue("Scheduled.Kombit.cron");
                    if (!string.IsNullOrEmpty(kombitCron))
                    {
                        kombitCron = fuzz(kombitCron);

                        s.ScheduleQuartzJob(q =>
                            q.WithJob(() => JobBuilder.Create<KombitRolesSyncJob>().Build())
                                .AddTrigger(() => TriggerBuilder.Create()
                                .WithCronSchedule(kombitCron)
                            .Build()));
                    }

                    string groupCron = Settings.GetStringValue("Scheduled.GroupSyncTask.cron");
                    if (!string.IsNullOrEmpty(groupCron))
                    {
                        groupCron = fuzz(groupCron);

                        s.ScheduleQuartzJob(q =>
                             q.WithJob(() => JobBuilder.Create<GroupSyncJob>().Build())
                                .AddTrigger(() => TriggerBuilder.Create()
                                .WithCronSchedule(groupCron)
                            .Build()));
                    }

                    string nsisCron = Settings.GetStringValue("Scheduled.NSISAllowedSyncTask.cron");
                    if (!string.IsNullOrEmpty(nsisCron))
                    {
                        nsisCron = fuzz(nsisCron);

                        s.ScheduleQuartzJob(q =>
                             q.WithJob(() => JobBuilder.Create<NSISAllowedSyncJob>().Build())
                                .AddTrigger(() => TriggerBuilder.Create()
                                .WithCronSchedule(nsisCron)
                            .Build()));
                    }

                    string nemLoginCron = Settings.GetStringValue("Scheduled.NemLoginAllowedSyncTask.cron");
                    if (!string.IsNullOrEmpty(nemLoginCron))
                    {
                        nemLoginCron = fuzz(nemLoginCron);

                        s.ScheduleQuartzJob(q =>
                             q.WithJob(() => JobBuilder.Create<TransferToNemloginSyncJob>().Build())
                                .AddTrigger(() => TriggerBuilder.Create()
                                .WithCronSchedule(nemLoginCron)
                            .Build()));
                    }

                    string kombitAttributeCron = Settings.GetStringValue("Scheduled.Kombit.Attributes.Cron");
                    if (!string.IsNullOrEmpty(kombitAttributeCron))
                    {
                        kombitAttributeCron = fuzz(kombitAttributeCron);

                        s.ScheduleQuartzJob(q =>
                             q.WithJob(() => JobBuilder.Create<KombitAttributesSyncJob>().Build())
                                .AddTrigger(() => TriggerBuilder.Create()
                                .WithCronSchedule(kombitAttributeCron)
                            .Build()));
                    }
                });
            });
        }

        private static string fuzz(string cron)
        {
            if (cron.StartsWith("0 "))
            {
                // use password as random seed
                string tmp = Settings.GetStringValue("Backend.Password");
                if (!string.IsNullOrEmpty(tmp) && tmp.Length >= 2)
                {
                    int x = (int)tmp[0] % 60;

                    return x + cron.Substring(1);
                }
            }

            return cron;
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
