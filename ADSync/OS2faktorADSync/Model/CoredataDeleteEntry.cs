using Newtonsoft.Json;
using System.Collections.Generic;
using System.Linq;

namespace OS2faktorADSync
{
    public class CoredataDeleteEntry
    {
        [JsonProperty(PropertyName = "uuid")]
        public string Uuid { get; set; }

        [JsonProperty(PropertyName = "cpr")]
        public string Cpr { get; set; }

        [JsonProperty(PropertyName = "samAccountName")]
        public string SamAccountName { get; set; }

        public CoredataDeleteEntry(CoredataEntry entry)
        {
            this.Uuid = entry.Uuid;
            this.Cpr = entry.Cpr;
            this.SamAccountName = entry.SamAccountName;
        }
    }
}
