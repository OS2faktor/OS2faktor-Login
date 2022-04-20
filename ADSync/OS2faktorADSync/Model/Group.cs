using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Threading.Tasks;

namespace OS2faktorADSync.Model
{
    class Group
    {
        public string DistinguishedName { get; set; }
        public string Name { get; set; }
        public string Description { get; set; }
        public string ObjectGuid { get; set; }

        public Group(string distinguishedName, string name, string description, string objectGuid)
        {
            DistinguishedName = distinguishedName;
            Name = name;
            Description = description;
            ObjectGuid = objectGuid;
        }
    }
}
