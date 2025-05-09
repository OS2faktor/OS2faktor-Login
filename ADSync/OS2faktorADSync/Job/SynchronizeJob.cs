﻿using Quartz;
using Serilog;
using System;
using System.Threading.Tasks;

namespace OS2faktorADSync
{
    [DisallowConcurrentExecution]
    public class SynchronizeJob : IJob
    {
        public ILogger Logger { get; set; }
        public ActiveDirectoryService ActiveDirectoryService { get; set; }
        public BackendService BackendService { get; set; }

        private byte[] directorySynchronizationCookie;
        private static bool shouldPerformFullSync = true;

        public static void ResetDirSync()
        {
            shouldPerformFullSync = true;
        }

        public Task Execute(IJobExecutionContext context)
        {
            bool disabled = Settings.GetBooleanValue("SyncJob.Disabled");
            if (disabled)
            {
                Logger.Debug("Job is disabled");
                return Task.CompletedTask;
            }

            if (shouldPerformFullSync)
            {
                shouldPerformFullSync = false; // in case of failure below, we do not want to go into an infinite full sync 
                Logger.Information("Performing full sync");
                try
                {
                    var users = ActiveDirectoryService.GetFullSyncUsers(out directorySynchronizationCookie);

                    BackendService.FullSync(users);
                }
                catch (Exception e)
                {
                    Logger.Error(e, "Exception caught in SynchronizeJob (full)");
                }

                Logger.Information("Full sync complete");
            }
            else
            {
                if (directorySynchronizationCookie == null || directorySynchronizationCookie.Length == 0)
                {
                    Log.Warning("No dirsync cookie, aborting!");
                }
                else
                {
                    try
                    {
                        Logger.Debug("Performing delta sync");

                        var users = ActiveDirectoryService.GetDeltaSyncUsers(ref directorySynchronizationCookie);

                        BackendService.DeltaSync(users.CreateEntries);
                        BackendService.DeltaDeleteSync(users.DeleteEntries);
                    }
                    catch (System.Exception e)
                    {
                        Logger.Error(e, "Exception caught in SynchronizeJob (delta)");
                    }
                }
            }

            return Task.CompletedTask;
        }
    }
}