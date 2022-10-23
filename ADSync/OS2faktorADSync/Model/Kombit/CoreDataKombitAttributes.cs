using Newtonsoft.Json;
using System.Collections.Generic;
using System.Linq;

namespace OS2faktorADSync.Model
{
    public class CoreDataKombitAttributes
    {
        [JsonProperty(PropertyName = "domain")]
        public string Domain { get; set; }

        [JsonProperty(PropertyName = "entryList")]
        public List<CoreDataKombitAttributeEntry> EntryList { get; set; }
    }
}
