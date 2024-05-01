using Newtonsoft.Json;

namespace OS2faktorADSync
{
    public class CoredataMitIDStatus
    {
        [JsonProperty(PropertyName = "cpr")]
        public string Cpr { get; set; }

        [JsonProperty(PropertyName = "samAccountName")]
        public string UserId { get; set; }

        [JsonProperty(PropertyName = "nemloginUserUuid")]
        public string Uuid { get; set; }

        [JsonProperty(PropertyName = "active")]
        public bool Active { get; set; }

    }
}