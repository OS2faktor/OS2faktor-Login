using System;
using System.Configuration;

namespace OS2faktor
{
    public class Settings
    {
        public bool GetBooleanValue(string key)
        {
            try
            {
                return Boolean.Parse(GetStringValue(key));
            }
            catch (Exception)
            {
                return false;
            }
        }

        public bool GetBooleanValueWithDefault(string key, bool defaultValue)
        {
            try
            {
                return Boolean.Parse(GetStringValue(key));
            }
            catch (Exception)
            {
                return defaultValue;
            }
        }

        public string GetStringValue(string key)
        {
            try
            {
                return ConfigurationManager.AppSettings[key];
            }
            catch (Exception)
            {
                return "";
            }
        }
    }
}
