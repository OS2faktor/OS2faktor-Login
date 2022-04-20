using Quartz;

namespace OS2faktor
{
    [DisallowConcurrentExecution]
    public class ReconnectJob : IJob
    {
        public WSCommunication webSocket { get; set; }

        public System.Threading.Tasks.Task Execute(IJobExecutionContext context)
        {
            webSocket.Connect();

            return System.Threading.Tasks.Task.CompletedTask;
        }
    }
}
