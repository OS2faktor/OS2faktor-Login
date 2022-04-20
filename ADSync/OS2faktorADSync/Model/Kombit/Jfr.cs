using Newtonsoft.Json;

namespace OS2faktorADSync.Model
{
    public class Jfr
    {
        [JsonProperty(PropertyName = "identifier")]
        public string Identifier { get; set; }

        [JsonProperty(PropertyName = "cvr")]
        public string CVR { get; set; }
    }
}