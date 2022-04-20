using Quartz;
using Serilog;
using System.Threading.Tasks;

namespace OS2faktorADSync
{
    [DisallowConcurrentExecution]
    public class GroupSyncJob : IJob
    {
        public ILogger Logger { get; set; }

        public ActiveDirectoryService ActiveDirectoryService { get; set; }

        public BackendService BackendService { get; set; }

        public Task Execute(IJobExecutionContext context)
        {
            Logger.Information("Performing group Sync");
            try
            {
                CoredataGroup groupData = ActiveDirectoryService.GroupSync();
                BackendService.GroupSync(groupData);
            }
            catch (System.Exception e)
            {
                Logger.Error(e, "Exception caught in GroupSyncJob");
            }

            return Task.CompletedTask;
        }
    }
}