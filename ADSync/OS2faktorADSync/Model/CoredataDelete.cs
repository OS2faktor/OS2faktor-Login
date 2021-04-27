using Newtonsoft.Json;
using System.Collections.Generic;

namespace OS2faktorADSync
{
    public class CoredataDelete
    {
        [JsonProperty(PropertyName = "domain")]
        public string Domain { get; set; }

        [JsonProperty(PropertyName = "entryList")]
        public List<CoredataDeleteEntry> EntryList { get; set; }
    }
}
