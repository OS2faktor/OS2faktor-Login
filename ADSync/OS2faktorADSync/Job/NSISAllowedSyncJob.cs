using Quartz;
using Serilog;
using System.Threading.Tasks;

namespace OS2faktorADSync
{
    [DisallowConcurrentExecution]
    public class NSISAllowedSyncJob : IJob
    {
        public ILogger Logger { get; set; }
        public ActiveDirectoryService ActiveDirectoryService { get; set; }
        public BackendService BackendService { get; set; }

        public Task Execute(IJobExecutionContext context)
        {
            Logger.Information("Performing NSIS allowed Sync");
            try
            {
                CoredataNSISAllowed nsisData = ActiveDirectoryService.NSISAllowedSync();
                BackendService.NSISSync(nsisData);
            }
            catch (System.Exception e)
            {
                System.Console.WriteLine("ERROR" + e);
                Logger.Error(e, "Exception caught in NSISAllowedSyncJob (full)");
            }

            Logger.Information("NSIS allowed Sync complete");

            return Task.CompletedTask;
        }
    }
}