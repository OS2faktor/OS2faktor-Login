using Newtonsoft.Json;
using System.Collections.Generic;
using System.Linq;

namespace OS2faktorADSync
{
    public class CoredataEntry
    {
        [JsonProperty(PropertyName = "uuid")]
        public string Uuid { get; set; }

        private string _cpr;
        [JsonProperty(PropertyName = "cpr")]
        public string Cpr {
            get
            {
                return _cpr;
            }
            set {
                this._cpr = null;
                
                if (value != null && value.Length >= 10 && value.Length <= 11)
                {
                    string tmp = value;

                    if (tmp.Length == 11)
                    {
                        tmp = tmp.Substring(0, 6) + tmp.Substring(6, 4);
                    }

                    if (tmp.All(char.IsDigit))
                    {
                        this._cpr = value;
                    }
                }
            }
        }

        [JsonProperty(PropertyName = "name")]
        public string Name { get; set; }

        [JsonProperty(PropertyName = "email")]
        public string Email { get; set; }

        [JsonProperty(PropertyName = "samAccountName")]
        public string SamAccountName { get; set; }

        [JsonProperty(PropertyName = "nsisAllowed")]
        public bool NSISAllowed { get; set; }

        [JsonProperty(PropertyName = "attributes")]
        public Dictionary<string, string> Attributes { get; set; }

        [JsonIgnore]
        public bool Deleted { get; set; }

        [JsonIgnore]
        public bool Disabled { get; set; }

        public bool IsActive()
        {
            return !Deleted && !Disabled;
        }

        public bool IsValid()
        {
            return !string.IsNullOrEmpty(Cpr) && !string.IsNullOrEmpty(Name);
        }
    }
}
