using OS2faktorADSync.Model;
using Quartz;
using Serilog;
using System.Collections.Generic;
using System.Linq;
using System.Threading.Tasks;

namespace OS2faktorADSync
{
    [DisallowConcurrentExecution]
    public class KombitRolesSyncJob : IJob
    {
        public ILogger Logger { get; set; }
        public ActiveDirectoryService ActiveDirectoryService { get; set; }
        public BackendService BackendService { get; set; }

        public Task Execute(IJobExecutionContext context)
        {
            Logger.Information("Performing full JFR sync");

            try
            {
                var userRoles = ActiveDirectoryService.KombitRolesSync();

                Logger.Debug($"Found {userRoles?.Count} JobFunctionRoles.");

                CoreDataFullJfr body = new CoreDataFullJfr();
                body.Domain = Settings.GetStringValue("Backend.Domain");
                body.EntryList = new List<CoreDataFullJfrEntry>();

                foreach (var entry in userRoles)
                {
                    CoreDataFullJfrEntry newCoreDataJfrEntry = new CoreDataFullJfrEntry();
                    newCoreDataJfrEntry.Uuid = entry.Key.Uuid;
                    newCoreDataJfrEntry.SamAccountName = entry.Key.SamAccountName;
                    newCoreDataJfrEntry.JobFunctionRoles = new List<Jfr>();

                    foreach (var role in entry.Value)
                    {
                        newCoreDataJfrEntry.JobFunctionRoles.Add(new Jfr() { Identifier = role.Identifier, CVR = role.Cvr });
                    }

                    body.EntryList.Add(newCoreDataJfrEntry);
                }

                //Logger.Debug($"Body send to OS2faktor backend:");
                //Logger.Debug(Newtonsoft.Json.JsonConvert.SerializeObject(body));

                BackendService.FullLoadKombitJfr(body);
            }
            catch (System.Exception e)
            {
                Logger.Error(e, "Exception caught in SynchronizeJob (full)");
            }

            Logger.Information("Full sync complete");

            return Task.CompletedTask;
        }
    }
}