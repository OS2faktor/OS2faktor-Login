using StructureMap;
using StructureMap.Pipeline;
using System;
using System.Linq;
using Serilog;

namespace OS2faktor
{
    public class LoggingForClassPolicy : ConfiguredInstancePolicy
    {
        protected override void apply(Type pluginType, IConfiguredInstance instance)
        {
            // Try to inject an ILogger via constructor parameter.
            var param = instance.Constructor.GetParameters().Where(x => x.ParameterType == typeof(ILogger)).FirstOrDefault();
            if (param != null)
            {
                var logger = new LoggerConfiguration().ReadFrom.AppSettings().CreateLogger().ForContext(pluginType);
                instance.Dependencies.AddForConstructorParameter(param, logger);
            }

            // Try to inject an Ilogger via property setting
            var property = instance.PluggedType.GetProperties().Where(x => x.PropertyType == typeof(ILogger)).FirstOrDefault();
            if (property != null)
            {
                var logger = new LoggerConfiguration().ReadFrom.AppSettings().CreateLogger().ForContext(pluginType);
                instance.Dependencies.AddForProperty(property, logger);
            }
        }
    }
}