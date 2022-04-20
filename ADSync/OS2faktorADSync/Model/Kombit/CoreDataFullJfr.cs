using Newtonsoft.Json;
using System.Collections.Generic;
using System.Linq;

namespace OS2faktorADSync.Model
{
    public class CoreDataFullJfr
    {
        [JsonProperty(PropertyName = "domain")]
        public string Domain { get; set; }

        [JsonProperty(PropertyName = "entryList")]
        public List<CoreDataFullJfrEntry> EntryList { get; set; }
    }
}
