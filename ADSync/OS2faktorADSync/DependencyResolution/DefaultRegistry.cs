using StructureMap;

namespace OS2faktorADSync
{
    public class DefaultRegistry : Registry
    {
        public DefaultRegistry()
        {
            Scan(_ =>
            {
                _.WithDefaultConventions();
            });

            // singleton required to persist dirsync cookie
            For<SynchronizeJob>().Singleton();

            // policies
            Policies.FillAllPropertiesOfType<ActiveDirectoryService>();
            Policies.FillAllPropertiesOfType<BackendService>();
            Policies.Add<LoggingForClassPolicy>();
        }
    }
}
