using Quartz;
using Serilog;
using System.Threading.Tasks;

namespace OS2faktorADSync
{
    [DisallowConcurrentExecution]
    public class TransferToNemloginSyncJob : IJob
    {
        public ILogger Logger { get; set; }
        public ActiveDirectoryService ActiveDirectoryService { get; set; }
        public BackendService BackendService { get; set; }

        public Task Execute(IJobExecutionContext context)
        {
            Logger.Information("Performing transfer to nemlogin Sync");
            try
            {
                CoredataTransferToNemLogin nemloginData = ActiveDirectoryService.TransferToNemloginSync();
                BackendService.NSISTransferToNemLoginSync(nemloginData);
            }
            catch (System.Exception e)
            {
                System.Console.WriteLine("ERROR" + e);
                Logger.Error(e, "Exception caught in TransferToNemloginSyncJob (full)");
            }

            Logger.Information("TransferToNemlogin Sync complete");

            return Task.CompletedTask;
        }
    }
}