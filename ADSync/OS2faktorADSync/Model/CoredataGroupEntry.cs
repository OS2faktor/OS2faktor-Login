using Newtonsoft.Json;
using System.Collections.Generic;

namespace OS2faktorADSync
{
    public class CoredataGroupEntry
    {
        [JsonProperty(PropertyName = "uuid")]
        public string Uuid { get; set; }

        [JsonProperty(PropertyName = "name")]
        public string Name { get; set; }
        [JsonProperty(PropertyName = "description")]
        public string Description { get; set; }

        [JsonProperty(PropertyName = "members")]
        public List<string> Members { get; set; }

        public CoredataGroupEntry()
        {
            this.Members = new List<string>();
        }
    }
}
