using System.Collections;
using System.Linq;

namespace OS2faktorADSync
{
    public static class DictionaryExtention
    {
        /**
         * Generic method that will return a dictionary value, the value of the first element in a value list, or a default value
         * This is used because the AD user's properties dictionary always contain lists with a single element if it's from a DirectorySearchResult even if it is not a list property
         */
        public static T GetValue<T>(this IDictionary dictionary, string key, T defaultValue)
        {
            // not all keys are required for configuration
            if (string.IsNullOrEmpty(key))
            {
                return defaultValue;
            }

            var value = dictionary[key.ToLower()];
            if (value is ICollection)
            {
                var collection = ((ICollection)value).OfType<T>();
                return collection.Count() > 0 ? collection.First() : defaultValue;
            }
            else
            {
                return value != null ? (T)value : defaultValue;
            }
        }
    }
}