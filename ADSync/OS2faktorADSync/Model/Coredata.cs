using Newtonsoft.Json;
using System.Collections.Generic;

namespace OS2faktorADSync
{
    public class Coredata
    {
        [JsonProperty(PropertyName = "domain")]
        public string Domain { get; set; }

        [JsonProperty(PropertyName = "entryList")]
        public List<CoredataEntry> EntryList { get; set; }
    }
}
