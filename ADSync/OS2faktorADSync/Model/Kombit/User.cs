using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Threading.Tasks;

namespace OS2faktorADSync.Model
{
    public class User : IEquatable<User>
    {
        public string Uuid { get; set; }
        public string SamAccountName { get; set; }

        public override int GetHashCode()
        {
            return Uuid.GetHashCode();
        }
        public override bool Equals(object obj)
        {
            return Equals(obj as User);
        }
        public bool Equals(User obj)
        {
            return obj != null && object.Equals(obj.Uuid, this.Uuid);
        }
    }
}
