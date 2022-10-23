using Newtonsoft.Json;
using System.Collections.Generic;

namespace OS2faktorADSync.Model
{
    public class CoreDataKombitAttributeEntry
    {
        [JsonProperty(PropertyName = "samAccountName")]
        public string SamAccountName { get; set; }


        [JsonProperty(PropertyName = "kombitAttributes")]
        public Dictionary<string, string> KombitAttributes { get; set; }

    }
}