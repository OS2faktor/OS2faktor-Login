using StructureMap;

namespace OS2faktor_Password_Agent
{
    public class DefaultRegistry : Registry
    {
        public DefaultRegistry()
        {
            Scan(_ =>
            {
                _.WithDefaultConventions();
            });

            For<WSCommunication>().Singleton();

            Policies.FillAllPropertiesOfType<WSCommunication>();
            Policies.FillAllPropertiesOfType<ADStub>();
            Policies.FillAllPropertiesOfType<ReconnectJob>();

            Policies.Add<LoggingForClassPolicy>();
        }
    }
}