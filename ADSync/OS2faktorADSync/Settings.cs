using System;
using System.Collections.Generic;
using System.Configuration;
using System.Linq;

namespace OS2faktorADSync
{
    class Settings
    {
        public static int GetIntValue(string key)
        {
            try
            {
                return Int32.Parse(GetStringValue(key));
            }
            catch (Exception)
            {
                return 0;
            }
        }

        public static bool GetBooleanValue(string key)
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

        public static string GetStringValue(string key)
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

        public static Dictionary<string, string> GetStringValues(string keyPrefix)
        {
            var result = new Dictionary<string, string>();
            var keys = ConfigurationManager.AppSettings.AllKeys.Where(k => k.StartsWith(keyPrefix)).ToList();
            foreach (var key in keys)
            {
                result.Add(key.Replace(keyPrefix, ""), ConfigurationManager.AppSettings[key]);
            }
            return result;
        }
    }
}
