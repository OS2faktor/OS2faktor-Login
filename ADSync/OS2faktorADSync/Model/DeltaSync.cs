using System.Collections.Generic;

namespace OS2faktorADSync
{
    public class DeltaSync
    {
        public List<CoredataDeleteEntry> DeleteEntries { get; set; }
        public List<CoredataEntry> CreateEntries { get; set; }
    }
}
