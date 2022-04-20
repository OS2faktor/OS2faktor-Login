using StructureMap;

namespace OS2faktor
{
    public class DefaultRegistry : Registry
    {
        public DefaultRegistry()
        {
            Scan(_ =>
            {
                _.TheCallingAssembly();
                _.WithDefaultConventions();
            });

            For<WSCommunication>().Singleton();

            Policies.FillAllPropertiesOfType<WSCommunication>();
            Policies.FillAllPropertiesOfType<ADStub>();

            Policies.Add<LoggingForClassPolicy>();
        }
    }
}