using Newtonsoft.Json;
using System.Collections.Generic;

namespace OS2faktorADSync
{
    public class CoredataGroup
    {
        [JsonProperty(PropertyName = "domain")]
        public string Domain { get; set; }

        [JsonProperty(PropertyName = "groups")]
        public List<CoredataGroupEntry> Groups { get; set; }

        public CoredataGroup()
        {
            this.Groups = new List<CoredataGroupEntry>();
        }
    }
}
