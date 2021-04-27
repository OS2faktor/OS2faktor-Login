using Quartz;
using Serilog;
using System.Threading.Tasks;

namespace OS2faktorADSync
{
    public class ResetDirSyncJob : IJob
    {
        public ILogger Logger { get; set; }

        public Task Execute(IJobExecutionContext context)
        {
            Logger.Debug("Resetting DirSync cookie");

            SynchronizeJob.ResetDirSync();

            return Task.CompletedTask;
        }
    }
}
