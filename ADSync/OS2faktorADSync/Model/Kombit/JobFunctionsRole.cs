using OS2faktorADSync.Model;

namespace OS2faktorADSync
{
    public class JobFunctionsRole
    {
        public string DistinguishedName { get; set; }
        public string Identifier { get; set; }
        public string Cvr { get; set; }

        public JobFunctionsRole(string distinguishedName, string identifier, string cvr)
        {
            this.DistinguishedName = distinguishedName;
            this.Identifier = identifier;
            this.Cvr = cvr;
        }
    }
}