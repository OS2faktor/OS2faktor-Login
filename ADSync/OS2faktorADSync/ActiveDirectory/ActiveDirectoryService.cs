using OS2faktorADSync.Model;
using Serilog;
using System;
using System.Collections;
using System.Collections.Generic;
using System.DirectoryServices;
using System.DirectoryServices.AccountManagement;
using System.DirectoryServices.ActiveDirectory;
using System.Linq;
using System.Runtime.InteropServices;
using System.Security;

namespace OS2faktorADSync
{
    public class ActiveDirectoryService
    {
        const int AccountDisable = 2;

        public ILogger Logger { get; set; }
        private readonly PropertyResolver propertyResolver = new PropertyResolver();
        private readonly string nsisAllowedGroup;
        private readonly string trustedEmployeesGroup;
        private readonly string transferToNemloginGroup;
        private readonly string privateMitIdGroup;
        private readonly string qualifiedSignatureGroup;
        private readonly string robotGroup;
        private readonly string rolesRoot;
        private readonly string rootMembershipGroup;
        private readonly Boolean groupsInGroups;
        private readonly string defaultCvr;
        private readonly string defaultDomain;
        private readonly string roleNamePrefix;
        private readonly long maxPasswordAgeConfig;
        private readonly string mitidErhvervUuidAttribute;
        private readonly string externalMitIdUUidAttribute;

        public bool MitIDBackSyncEnabled { get { return !string.IsNullOrEmpty(mitidErhvervUuidAttribute); } }

        public ActiveDirectoryService()
        {
            trustedEmployeesGroup = Settings.GetStringValue("ActiveDirectory.TrustedEmployees.Group");
            nsisAllowedGroup = Settings.GetStringValue("ActiveDirectory.NSISAllowed.Group");
            transferToNemloginGroup = Settings.GetStringValue("ActiveDirectory.TransferToNemlogin.Group");
            privateMitIdGroup = Settings.GetStringValue("ActiveDirectory.PrivateMitID.Group");
            qualifiedSignatureGroup = Settings.GetStringValue("ActiveDirectory.QualifiedSignature.Group");
            robotGroup = Settings.GetStringValue("ActiveDirectory.Robot.Group");
            rootMembershipGroup = Settings.GetStringValue("ActiveDirectory.Group.Root");
            rolesRoot = Settings.GetStringValue("Kombit.RoleOU");
            defaultDomain = Settings.GetStringValue("Kombit.RoleDomainDefault");
            defaultCvr = Settings.GetStringValue("Kombit.RoleCvrDefault");
            groupsInGroups = Settings.GetBooleanValue("Kombit.GroupsInGroups");
            roleNamePrefix = Settings.GetStringValue("Kombit.RoleNameAttributePrefix");
            maxPasswordAgeConfig = (long) Settings.GetIntValue("ActiveDirectory.MaxPasswordAge");
            mitidErhvervUuidAttribute = Settings.GetStringValue("ActiveDirectory.Property.MitIDErhvervUuid");
        }

        public IEnumerable<CoredataEntry> GetFullSyncUsers(out byte[] directorySynchronizationCookie)
        {
            Dictionary<string, string> sAMAccountNameToCprMap = SQLService.GetSAMAccountNameToCprMap();
            long maxPasswordAge = GetMaxPasswordAge();

            Logger.Information("Max Password Age set to " + maxPasswordAge);

            using (var directoryEntry = GenerateDirectoryEntry())
            {
                List<string> invalids = new List<string>();
                Dictionary<string, CoredataEntry> result = new Dictionary<string, CoredataEntry>();
                string cprFilter = string.IsNullOrEmpty(propertyResolver.ExternalMitIdUuidProperty)
                    ? (propertyResolver.CprProperty + "=*")
                    : ("(|(" + propertyResolver.ExternalMitIdUuidProperty + "=*)(" + propertyResolver.CprProperty + "=*))");

                string filter = CreateFilter("!(isDeleted=TRUE)", cprFilter);

                Logger.Debug("Performing search with filter: " + filter);

                using (var directorySearcher = new DirectorySearcher(directoryEntry, filter, propertyResolver.AllProperties, SearchScope.Subtree))
                {
                    directorySearcher.DirectorySynchronization = new DirectorySynchronization(DirectorySynchronizationOptions.None);
                    using (var searchResultCollection = directorySearcher.FindAll())
                    {
                        Logger.Information("Found {0} users in Active Directory", searchResultCollection.Count);

                        foreach (SearchResult searchResult in searchResultCollection)
                        {
                            CoredataEntry user = CreateCoredataEntryFromAD(searchResult.Properties, sAMAccountNameToCprMap, maxPasswordAge);
                            if (user.IsActive() && user.IsValid())
                            {
                                if (result.ContainsKey(user.Uuid))
                                {
                                    Logger.Warning("User with uuid " + user.Uuid + " existed twice: " + user.SamAccountName + " / " + (result[user.Uuid].SamAccountName));
                                }
                                else
                                {
                                    result.Add(user.Uuid, user);
                                }
                            }

                            if (!user.IsValid())
                            {
                                invalids.Add(user.SamAccountName);
                                Logger.Verbose("User has been filtered out. Not valid: " + user.SamAccountName);
                            }
                        }

                        if (invalids.Count > 0)
                        {
                            Logger.Warning(invalids.Count + " user(s) have been filtered away. Log verbose for more information.");
                        }

                        Logger.Information("{0} users where active and valid", result.Count);
                    }

                    directorySynchronizationCookie = directorySearcher.DirectorySynchronization.GetDirectorySynchronizationCookie();
                }

                List<string> nsisAllowedUsers = GetTransativeGroupMembership(directoryEntry, "nsisAllowedGroup", nsisAllowedGroup);
                foreach (string guid in nsisAllowedUsers)
                {
                    if (result.ContainsKey(guid))
                    {
                        result[guid].NSISAllowed = true;
                    }
                }

                List<string> trustedEmployeesUsers = GetTransativeGroupMembership(directoryEntry, "trustedEmployeesGroup", trustedEmployeesGroup);
                foreach (string guid in trustedEmployeesUsers)
                {
                    if (result.ContainsKey(guid))
                    {
                        result[guid].TrustedEmployee = true;
                    }
                }

                List<string> transferToNemloginUsers = GetTransativeGroupMembership(directoryEntry, "transferToNemloginGroup", transferToNemloginGroup);
                foreach (string guid in transferToNemloginUsers)
                {
                    if (result.ContainsKey(guid))
                    {
                        result[guid].TransferToNemlogin = true;
                    }
                }

                List<string> privateMitIDUsers = GetTransativeGroupMembership(directoryEntry, "privateMitIdGroup", privateMitIdGroup);
                foreach (string guid in privateMitIDUsers)
                {
                    if (result.ContainsKey(guid))
                    {
                        result[guid].PrivateMitID = true;
                    }
                }

                List<string> qualifiedSignatureUsers = GetTransativeGroupMembership(directoryEntry, "qualifiedSignatureGroup", qualifiedSignatureGroup);
                foreach (string guid in qualifiedSignatureUsers)
                {
                    if (result.ContainsKey(guid))
                    {
                        result[guid].QualifiedSignature = true;
                    }
                }

                List<string> robotUsers = GetTransativeGroupMembership(directoryEntry, "robotGroup", robotGroup);
                foreach (string guid in robotUsers)
                {
                    if (result.ContainsKey(guid))
                    {
                        result[guid].Robot = true;
                    }
                }

                return result.Values;
            }
        }

        private List<string> GetTransativeGroupMembership(DirectoryEntry directoryEntry, string groupProperty, string groupName)
        {
            List<string> result = new List<string>();

            if (!string.IsNullOrEmpty(groupName))
            {
                Logger.Debug($"Performing lookup of membership in {groupProperty} group");
                if (!GroupExists(groupName))
                {
                    throw new Exception($"{groupProperty} was configured, but a matching group was not found, aborting");
                }

                // Additional search for members of a specific group (Recursive / transative)
                string cprFilter = string.IsNullOrEmpty(propertyResolver.ExternalMitIdUuidProperty)
                    ? (propertyResolver.CprProperty + "=*")
                    : ("(|(" + propertyResolver.ExternalMitIdUuidProperty + "=*)(" + propertyResolver.CprProperty + "=*))");

                string filter = CreateFilter("!(isDeleted=TRUE)", cprFilter, "memberOf:1.2.840.113556.1.4.1941:=" + groupName);

                Logger.Debug("Performing search with filter: " + filter);

                using (var directorySearcher = new DirectorySearcher(directoryEntry, filter, propertyResolver.AllProperties, SearchScope.Subtree))
                {
                    directorySearcher.PageSize = 1000;

                    using (var searchResultCollection = directorySearcher.FindAll())
                    {
                        Logger.Debug($"Found {searchResultCollection.Count} users in {groupProperty} group");

                        foreach (SearchResult searchResult in searchResultCollection)
                        {
                            string Guid = fetchGuid(searchResult);
                            if (!string.IsNullOrEmpty(Guid) )
                            {
                                result.Add(Guid);
                            }
                            else
                            {
                                Logger.Debug("Could not match user with ObjectGuid " + Guid + " to any user previously found");
                            }
                        }
                    }
                }
            }

            return result;
        }

        public DeltaSync GetDeltaSyncUsers(ref byte[] directorySynchronizationCookie)
        {
            DeltaSync result = new DeltaSync();
            result.CreateEntries = new List<CoredataEntry>();
            result.DeleteEntries = new List<CoredataDeleteEntry>();

            long maxPasswordAge = -1;

            using (var directoryEntry = GenerateDirectoryEntry())
            {
                string cprFilter = string.IsNullOrEmpty(propertyResolver.ExternalMitIdUuidProperty)
                    ? (propertyResolver.CprProperty + "=*")
                    : ("(|(" + propertyResolver.ExternalMitIdUuidProperty + "=*)(" + propertyResolver.CprProperty + "=*))");

                string filter = CreateFilter("!(isDeleted=TRUE)", cprFilter);

                Logger.Debug("Performing search with filter: " + filter);

                using (var directorySearcher = new DirectorySearcher(directoryEntry, filter, propertyResolver.AllProperties, SearchScope.Subtree))
                {
                    directorySearcher.DirectorySynchronization = new DirectorySynchronization(DirectorySynchronizationOptions.None, directorySynchronizationCookie);

                    using (var searchResults = directorySearcher.FindAll())
                    {
                        foreach (SearchResult searchResult in searchResults)
                        {
                            Logger.Verbose("Delta sync searchResult: {@searchResult}", searchResult);

                            if (searchResult.Properties.GetValue<Boolean>(propertyResolver.DeletedProperty, false))
                            {
                                Logger.Information("Received delta sync deleted object {0}", searchResult.Path);
                            }
                            else if (searchResult.Path.Contains("CN=Deleted Objects"))
                            {
                                Logger.Information("Object purged from AD Deleted Objects. Ignoring. {0}", searchResult.Path);
                            }
                            else
                            {
                                // only perform lookup if we actually have hits
                                if (maxPasswordAge == -1)
                                {
                                    maxPasswordAge = GetMaxPasswordAge();
                                }

                                Logger.Information("Received delta sync user {0}", searchResult.Path);

                                //get properties from the directoryEntry because the delta search result only contain the changed properties
                                var coreDataEntry = CreateCoredataEntryFromAD(searchResult.GetDirectoryEntry().Properties, null, maxPasswordAge);
                                if (coreDataEntry.IsValid())
                                {
                                    if (coreDataEntry.IsActive())
                                    {
                                        result.CreateEntries.Add(coreDataEntry);
                                    }
                                    else
                                    {
                                        result.DeleteEntries.Add(new CoredataDeleteEntry(coreDataEntry));
                                    }
                                }
                            }
                        }
                    }

                    directorySynchronizationCookie = directorySearcher.DirectorySynchronization.GetDirectorySynchronizationCookie();
                }

                if (!string.IsNullOrEmpty(transferToNemloginGroup) && !GroupExists(transferToNemloginGroup))
                {
                    throw new Exception("transferToNemloginGroup was configured, but a matching group was not found, aborting");
                }

                if (!string.IsNullOrEmpty(nsisAllowedGroup) && !GroupExists(nsisAllowedGroup))
                {
                    throw new Exception("nsisAllowedGroup was configured, but a matching group was not found, aborting");
                }

                if (!string.IsNullOrEmpty(trustedEmployeesGroup) && !GroupExists(trustedEmployeesGroup))
                {
                    throw new Exception("trustedEmployeesGroup was configured, but a matching group was not found, aborting");
                }

                if (!string.IsNullOrEmpty(privateMitIdGroup) && !GroupExists(privateMitIdGroup))
                {
                    throw new Exception("privateMitIdGroup was configured, but a matching group was not found, aborting");
                }

                if (!string.IsNullOrEmpty(qualifiedSignatureGroup) && !GroupExists(qualifiedSignatureGroup))
                {
                    throw new Exception("qualifiedSignatureGroup was configured, but a matching group was not found, aborting");
                }

                if (!string.IsNullOrEmpty(robotGroup) && !GroupExists(robotGroup))
                {
                    throw new Exception("robotGroup was configured, but a matching group was not found, aborting");
                }

                foreach (CoredataEntry entry in result.CreateEntries)
                {
                    entry.NSISAllowed = GetTransativeMembershipForEntry(directoryEntry, entry.SamAccountName, "nsisAllowedGroup", nsisAllowedGroup);
                    entry.TransferToNemlogin = GetTransativeMembershipForEntry(directoryEntry, entry.SamAccountName, "transferToNemloginGroup", transferToNemloginGroup);
                    entry.PrivateMitID = GetTransativeMembershipForEntry(directoryEntry, entry.SamAccountName, "privateMitIdGroup", privateMitIdGroup);
                    entry.QualifiedSignature = GetTransativeMembershipForEntry(directoryEntry, entry.SamAccountName, "qualifiedSignatureGroup", qualifiedSignatureGroup);
                    entry.TrustedEmployee = GetTransativeMembershipForEntry(directoryEntry, entry.SamAccountName, "trustedEmployeeGroup", trustedEmployeesGroup);
                    entry.Robot = GetTransativeMembershipForEntry(directoryEntry, entry.SamAccountName, "robotGroup", robotGroup);
                }

                return result;
            }
        }

        private bool GetTransativeMembershipForEntry(DirectoryEntry directoryEntry, string samAccountName, string groupProperty, string groupName)
        {
            // if not configured, just return false
            if (string.IsNullOrEmpty(groupName))
            {
                return false;
            }

            if (!string.IsNullOrEmpty(groupName) && !GroupExists(groupName))
            {
                throw new Exception($"{groupProperty} was configured, but a matching group was not found, aborting");
            }

            string cprFilter = string.IsNullOrEmpty(propertyResolver.ExternalMitIdUuidProperty)
                ? (propertyResolver.CprProperty + "=*")
                : ("(|(" + propertyResolver.ExternalMitIdUuidProperty + "=*)(" + propertyResolver.CprProperty + "=*))");

            string filter = CreateFilter("!(isDeleted=TRUE)", cprFilter, propertyResolver.SAMAccountNameProperty + "=" + samAccountName, "memberOf:1.2.840.113556.1.4.1941:=" + groupName);
            using (var directorySearcher = new DirectorySearcher(directoryEntry, filter, propertyResolver.AllProperties, SearchScope.Subtree))
            {
                directorySearcher.PageSize = 1000;

                using (var searchResultCollection = directorySearcher.FindAll())
                {
                    if (searchResultCollection.Count == 1)
                    {
                        Logger.Debug($"{samAccountName} is in {groupProperty} group");
                        return true;
                    }
                    else
                    {
                        Logger.Debug($"{samAccountName} is NOT in {groupProperty} group");
                    }
                }
            }

            return false;
        }

        public void UpdateMitIDUUID(List<CoredataMitIDStatus> status)
        {
            string cprFilter = string.IsNullOrEmpty(propertyResolver.ExternalMitIdUuidProperty)
                ? (propertyResolver.CprProperty + "=*")
                : ("(|(" + propertyResolver.ExternalMitIdUuidProperty + "=*)(" + propertyResolver.CprProperty + "=*))");

            string filter = CreateFilter("!(isDeleted=TRUE)", cprFilter);

            using (var context = new PrincipalContext(ContextType.Domain))
            {
                using (var searcher = new PrincipalSearcher(new UserPrincipal(context)))
                {
                    ((DirectorySearcher)searcher.GetUnderlyingSearcher()).PageSize = 1000;
                    ((DirectorySearcher)searcher.GetUnderlyingSearcher()).Filter = filter;

                    using (var searchResult = searcher.FindAll())
                    {
                        foreach (var principal in searchResult)
                        {
                            UserPrincipal user = (UserPrincipal)principal;
                            string userId = user.SamAccountName.ToLower();

                            DirectoryEntry de = user.GetUnderlyingObject() as DirectoryEntry;
                            if (de.Properties.Contains(propertyResolver.MitIDUuidProperty))
                            {
                                string currentUuid = de.Properties[propertyResolver.MitIDUuidProperty].Value.ToString();

                                bool found = false;
                                foreach (var stati in status)
                                {
                                    if (stati.UserId.ToLower().Equals(userId))
                                    {
                                        if (!currentUuid.Equals(stati.Uuid))
                                        {
                                            Logger.Information("Updating MitID UUID on " + userId + " to " + stati.Uuid);
                                            de.Properties[propertyResolver.MitIDUuidProperty].Value = stati.Uuid;
                                            de.CommitChanges();
                                        }

                                        found = true;
                                        break;
                                    }
                                }

                                if (!found)
                                {
                                    Logger.Information("Clearing MitID UUID on " + userId);

                                    de.Properties[propertyResolver.MitIDUuidProperty].Clear();
                                    de.CommitChanges();
                                }
                            }
                            else
                            {
                                foreach (var stati in status)
                                {
                                    if (stati.UserId.ToLower().Equals(userId))
                                    {
                                        Logger.Information("Setting MitID UUID on " + userId + " to " + stati.Uuid);
                                        de.Properties[propertyResolver.MitIDUuidProperty].Value = stati.Uuid;
                                        de.CommitChanges();
                                        break;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // TODO: burde være muligt at lave en dirSyncCookie på dette
        public CoredataNSISAllowed NSISAllowedSync()
        {
            if (string.IsNullOrEmpty(nsisAllowedGroup))
            {
                throw new Exception("nsisAllowedGroup not configured, aborting");
            }

            if (!GroupExists(nsisAllowedGroup))
            {
                throw new Exception("nsisAllowedGroup was configured, but a matching group was not found, aborting");
            }

            CoredataNSISAllowed coredataNSISAllowed = new CoredataNSISAllowed();

            using (var directoryEntry = GenerateDirectoryEntry())
            {
                // Additional search for members of a specific group (Recursive / transative)
                string cprFilter = string.IsNullOrEmpty(propertyResolver.ExternalMitIdUuidProperty)
                    ? (propertyResolver.CprProperty + "=*")
                    : ("(|(" + propertyResolver.ExternalMitIdUuidProperty + "=*)(" + propertyResolver.CprProperty + "=*))");

                string filter = CreateFilter("!(isDeleted=TRUE)", cprFilter, "memberOf:1.2.840.113556.1.4.1941:=" + nsisAllowedGroup);
                using (var directorySearcher = new DirectorySearcher(directoryEntry, filter, propertyResolver.AllProperties, SearchScope.Subtree))
                {
                    directorySearcher.PageSize = 1000;

                    using (var searchResultCollection = directorySearcher.FindAll())
                    {
                        foreach (SearchResult searchResult in searchResultCollection)
                        {
                            string Guid = fetchGuid(searchResult);
                            if (!string.IsNullOrEmpty(Guid))
                            {
                                coredataNSISAllowed.NSISAllowed.Add(Guid);
                            }
                        }
                    }
                }
            }

            Logger.Information("Found {0} nsis users in NSISAllowedGroup", coredataNSISAllowed.NSISAllowed.Count());

            return coredataNSISAllowed;
        }

        // TODO: burde være muligt at lave en dirSyncCookie på dette
        public CoredataTransferToNemLogin TransferToNemloginSync()
        {
            if (string.IsNullOrEmpty(transferToNemloginGroup))
            {
                throw new Exception("transferToNemloginGroup not configured, aborting");
            }

            if (!GroupExists(transferToNemloginGroup))
            {
                throw new Exception("transferToNemloginGroup was configured, but a matching group was not found, aborting");
            }

            CoredataTransferToNemLogin coredataTransferToNemlogin = new CoredataTransferToNemLogin();

            using (var directoryEntry = GenerateDirectoryEntry())
            {
                // Additional search for members of a specific group (Recursive / transative)
                string filter = CreateFilter("!(isDeleted=TRUE)", propertyResolver.CprProperty + "=*", "memberOf:1.2.840.113556.1.4.1941:=" + transferToNemloginGroup);
                using (var directorySearcher = new DirectorySearcher(directoryEntry, filter, propertyResolver.AllProperties, SearchScope.Subtree))
                {
                    directorySearcher.PageSize = 1000;

                    using (var searchResultCollection = directorySearcher.FindAll())
                    {
                        foreach (SearchResult searchResult in searchResultCollection)
                        {
                            string Guid = fetchGuid(searchResult);
                            if (!string.IsNullOrEmpty(Guid))
                            {
                                coredataTransferToNemlogin.TransferToNemLogin.Add(Guid);
                            }
                        }
                    }
                }
            }

            Logger.Information("Found {0} nsis users in transfer to nemlogin Group", coredataTransferToNemlogin.TransferToNemLogin.Count());

            return coredataTransferToNemlogin;
        }

        // TODO: også en DirSyncCookie her
        public CoredataGroup GroupSync()
        {
            using (var directoryEntry = GenerateDirectoryEntry())
            {
                // Populate groups property of coredata to syncronize groups in ad with groups in os2faktor login
                if (!string.IsNullOrEmpty(rootMembershipGroup))
                {
                    if (!GroupExists(rootMembershipGroup))
                    {
                        throw new Exception("rootMembershipGroup was configured, but a matching group was not found, aborting");
                    }

                    // Find group of members os2faktor "cares about"
                    string filter = CreateGroupFilter("!(isDeleted=TRUE)", "memberOf=" + rootMembershipGroup);

                    List<Group> groups = new List<Group>();
                    using (var directorySearcher = new DirectorySearcher(directoryEntry, filter, propertyResolver.GroupProperties, SearchScope.Subtree))
                    {
                        directorySearcher.PageSize = 1000;

                        using (var searchResultCollection = directorySearcher.FindAll())
                        {
                            Logger.Information("Found {0} groups to send to OS2faktor", searchResultCollection.Count);

                            foreach (SearchResult searchResult in searchResultCollection)
                            {
                                string Guid = new Guid(searchResult.Properties.GetValue<System.Byte[]>("objectGUID", null)).ToString();
                                string GroupDN = searchResult.Properties.GetValue<string>(propertyResolver.DistinguishedNameProperty, null);
                                string GroupName = searchResult.Properties.GetValue<string>(propertyResolver.NameProperty, null);
                                string groupDescription = searchResult.Properties.GetValue<string>(propertyResolver.DescriptionProperty, null);
                                groups.Add(new Group(GroupDN, GroupName, groupDescription, Guid));
                            }
                        }
                    }

                    // Find all members of those groups and set them as a property on coredata entries
                    CoredataGroup coredataGroup = new CoredataGroup();
                    foreach (Group group in groups)
                    {
                        CoredataGroupEntry entry = new CoredataGroupEntry();
                        entry.Uuid = group.ObjectGuid;
                        entry.Name = group.Name;
                        entry.Description = group.Description;

                        filter = CreateMembeshipFilter(propertyResolver.CprProperty + "=*", "memberOf:1.2.840.113556.1.4.1941:=" + group.DistinguishedName);

                        using (var directorySearcher = new DirectorySearcher(directoryEntry, filter, propertyResolver.AllProperties, SearchScope.Subtree))
                        {
                            directorySearcher.PageSize = 1000;

                            using (var searchResultCollection = directorySearcher.FindAll())
                            {
                                Logger.Information("Found {0} members of group ({1})", searchResultCollection.Count, group.DistinguishedName);
                                foreach (SearchResult searchResult in searchResultCollection)
                                {
                                    string memberGUID = fetchGuid(searchResult);
                                    if (!string.IsNullOrEmpty(memberGUID))
                                    {
                                        entry.Members.Add(memberGUID);
                                    }
                                }
                            }
                        }

                        coredataGroup.Groups.Add(entry);
                    }

                    return coredataGroup;
                }
            }

            return null;
        }

        public Dictionary<User, List<JobFunctionsRole>> KombitRolesSync()
        {
            if (string.IsNullOrEmpty(rolesRoot))
            {
                Logger.Warning("Kombit.RoleOU configuration setting is empty");
                return null;
            }

            // read out optional ouFilter
            string ouFilter = Settings.GetStringValue("Kombit.RoleOU.Filter");

            Dictionary<User, List<JobFunctionsRole>> result = new Dictionary<User, List<JobFunctionsRole>>();
            List<JobFunctionsRole> groups = new List<JobFunctionsRole>();

            using (var directoryEntry = GenerateDirectoryEntry())
            {
                directoryEntry.Path += "/" + rolesRoot;

                string filter = string.IsNullOrEmpty(ouFilter) ? CreateGroupFilter("!(isDeleted=TRUE)") : CreateGroupFilter("!(isDeleted=TRUE)", ouFilter);

                using (var directorySearcher = new DirectorySearcher(directoryEntry, filter, propertyResolver.KombitProperties, SearchScope.Subtree))
                {
                    directorySearcher.PageSize = 1000;

                    using (var searchResultCollection = directorySearcher.FindAll())
                    {
                        Logger.Information("Found {0} groups to find roles from", searchResultCollection.Count);
                        foreach (SearchResult searchResult in searchResultCollection)
                        {
                            string dn = searchResult.Properties.GetValue<string>(propertyResolver.DistinguishedNameProperty, null);
                            // TODO: in SG case the altSecurityIdentities is multi-value... this seems to give us the LAST of the values
                            string roleName = searchResult.Properties.GetValue<string>(propertyResolver.RoleNameProperty, null);
                            string roleDomain = (string.IsNullOrEmpty(propertyResolver.RoleDomainProperty)) ? null : searchResult.Properties.GetValue<string>(propertyResolver.RoleDomainProperty, null);
                            string cvr = (string.IsNullOrEmpty(propertyResolver.RoleCvrProperty)) ? null : searchResult.Properties.GetValue<string>(propertyResolver.RoleCvrProperty, null);

                            if (roleName == null)
                            {
                                Logger.Debug($"Group {dn} does not have value for property {propertyResolver.RoleNameProperty}");
                                continue;
                            }

                            // if a prefix is configured, and this role starts with this prefix, remove it
                            if (!string.IsNullOrEmpty(roleNamePrefix) && roleName.StartsWith(roleNamePrefix))
                            {
                                roleName = roleName.Substring(roleNamePrefix.Length);

                                if (roleName.Contains("#"))
                                {
                                    string fullName = roleName;

                                    int idx = fullName.IndexOf("#");
                                    roleName = fullName.Substring(0, idx);
                                    fullName = fullName.Substring(idx + 1);
                                    if (fullName.Contains("_"))
                                    {
                                        idx = fullName.IndexOf("_");
                                        cvr = fullName.Substring(0, idx);
                                        roleDomain = fullName.Substring(idx + 1);
                                    }
                                }
                            }

                            // special corner-case for SGs mapping solution
                            if (roleName.Contains("<KOMBIT><JFR>"))
                            {
                                try
                                {
                                    string txt = roleName.Substring("<KOMBIT><JFR>".Length);

                                    // delegated?
                                    if (txt.Contains("<CVR>") && txt.Contains("<ISSUER>"))
                                    {
                                        // expected formats
                                        // <KOMBIT><JFR>roleName<CVR>cvr<ISSUER>roleDomain
                                        // <KOMBIT><JFR>roleName<CVR>cvr<ISSUER>roleDomain</JFR></KOMBIT>

                                        int cvrIdx = txt.IndexOf("<CVR>");
                                        int issuerIdx = txt.IndexOf("<ISSUER>");

                                        // pull out roleName
                                        roleName = txt.Substring(0, cvrIdx);

                                        // pull out cvr
                                        cvr = txt.Substring(cvrIdx + 5, (issuerIdx - cvrIdx - 5));

                                        // pull out roleDomain
                                        roleDomain = txt.Substring(issuerIdx + 8);

                                        if (roleDomain.Contains("</JFR></KOMBIT>"))
                                        {
                                            int idx = roleDomain.IndexOf("</JFR></KOMBIT>");
                                            roleDomain = roleDomain.Substring(0, idx);
                                        }

                                        if (roleDomain.StartsWith("http://"))
                                        {
                                            roleDomain = roleDomain.Substring(7);
                                        }

                                        if (roleDomain.EndsWith("/"))
                                        {
                                            roleDomain = roleDomain.Substring(0, roleDomain.Length - 1);
                                        }
                                    }
                                    else
                                    {
                                        // expected formats
                                        // <KOMBIT><JFR>roleName</JFR></KOMBIT>
                                        // <KOMBIT><JFR>roleName

                                        roleName = txt;
                                        if (roleName.Contains("</JFR></KOMBIT>"))
                                        {
                                            int idx = roleName.IndexOf("</JFR></KOMBIT>");
                                            roleName = roleName.Substring(0, idx);
                                        }
                                    }
                                }
                                catch (Exception ex)
                                {
                                    Logger.Warning(ex, "Failed to parse content of roleName attribute: " + roleName);
                                }
                            }
                            // so not <KOMBIT><JFR>, but still a <KOMBIT> value, so skip it
                            else if (roleName.Contains("<KOMBIT>"))
                            {
                                Logger.Debug($"Skipping {dn} as it is not a real JFR group");
                                continue;
                            }

                            if (string.IsNullOrEmpty(roleDomain))
                            {
                                roleDomain = defaultDomain;
                            }

                            if (string.IsNullOrEmpty(cvr))
                            {
                                cvr = defaultCvr;
                            }

                            // for some roles, the name or domain actually already contains the full identifier, so respect that.
                            // this is used by a bunch of municipalies in their existing setup, so might as well allow it
                            string roleIdentifier = $"http://{roleDomain}/roles/jobrole/{roleName}/1";
                            if (roleName.StartsWith("http://"))
                            {
                                roleIdentifier = roleName;
                            }
                            else if (roleDomain.StartsWith("http://"))
                            {
                                roleIdentifier = roleName;
                            }

                            // convert AD group to role object
                            groups.Add(new JobFunctionsRole(dn, roleIdentifier, cvr));
                        }
                    }
                }
            }

            // new connection, so we search AD-wide
            using (var directoryEntry = GenerateDirectoryEntry())
            {
                // find all members of those groups
                foreach (JobFunctionsRole group in groups)
                {
                    string filter;

                    if (groupsInGroups)
                    {
                        filter = CreateMembeshipFilter(propertyResolver.CprProperty + "=*", "memberOf:1.2.840.113556.1.4.1941:=" + group.DistinguishedName);
                    }
                    else
                    {
                        filter = CreateMembeshipFilter(propertyResolver.CprProperty + "=*", "memberOf=" + group.DistinguishedName);
                    }

                    Logger.Debug("Searching for members using filter: " + filter);

                    using (var directorySearcher = new DirectorySearcher(directoryEntry, filter, propertyResolver.AllProperties, SearchScope.Subtree))
                    {
                        directorySearcher.PageSize = 1000;

                        using (var searchResultCollection = directorySearcher.FindAll())
                        {
                            Logger.Information("Found {0} members of group ({1})", searchResultCollection.Count, group.DistinguishedName);

                            foreach (SearchResult searchResult in searchResultCollection)
                            {
                                string memberGUID = fetchGuid(searchResult);
                                if (!string.IsNullOrEmpty(memberGUID))
                                {
                                    string memberSamAccountName = searchResult.Properties.GetValue<string>(propertyResolver.SAMAccountNameProperty, null);

                                    User user = new User { SamAccountName = memberSamAccountName, Uuid = memberGUID };

                                    if (!result.ContainsKey(user))
                                    {
                                        result.Add(user, new List<JobFunctionsRole>());
                                    }

                                    result[user].Add(group);
                                }
                            }
                        }
                    }
                }
            }

            return result;
        }

        private bool GroupExists(string distinguishedName)
        {
            using (var directoryEntry = GenerateDirectoryEntry())
            {
                string filter = CreateGroupFilter("!(isDeleted=TRUE)", "distinguishedname=" + distinguishedName);
                using (var directorySearcher = new DirectorySearcher(directoryEntry, filter, propertyResolver.GroupProperties, SearchScope.Subtree))
                {
                    SearchResult searchResult = directorySearcher.FindOne();
                    bool exists = searchResult != null;

                    if (!exists)
                    {
                        Logger.Error($"Failed GroupExists check for distinguishedName = {distinguishedName}");
                    }

                    return exists;
                }
            }
        }

        private DirectoryEntry GenerateDirectoryEntry()
        {
            DirectoryEntry directoryEntry = null;

            DomainControllerCollection domains = Domain.GetCurrentDomain().DomainControllers;
            foreach (DomainController controller in domains)
            {
                try
                {
                    directoryEntry = new DirectoryEntry(string.Format("LDAP://{0}", controller.Name));

                    if (directoryEntry.Properties.Count > 0)
                    {
                        // accessing the nativeObject will throw an exception if we're not really connected to an operational DC
                        var nativeObejct = directoryEntry.NativeObject;
                        Logger.Verbose("Connected to " + controller.Name);
                        break;
                    }
                }
                catch (Exception ex)
                {
                    Logger.Warning("Failed to connect to " + controller.Name + ". Reason:" + ex.Message);
                }
            }

            if (directoryEntry == null)
            {
                throw new Exception("Failed to connect to AD");
            }

            var username = Settings.GetStringValue("ActiveDirectory.Username");
            var password = Settings.GetStringValue("ActiveDirectory.Password");

            if (!string.IsNullOrEmpty(username) && !string.IsNullOrEmpty(password))
            {
                directoryEntry.Username = username;
                directoryEntry.Password = password;
            }

            return directoryEntry;
        }


        [ComImport, Guid("9068270b-0939-11d1-8be1-00c04fd8d503"), InterfaceType(ComInterfaceType.InterfaceIsDual)]
        internal interface IAdsLargeInteger
        {
            long HighPart
            {
                [SuppressUnmanagedCodeSecurity]
                get; [SuppressUnmanagedCodeSecurity]
                set;
            }

            long LowPart
            {
                [SuppressUnmanagedCodeSecurity]
                get; [SuppressUnmanagedCodeSecurity]
                set;
            }
        }

        private CoredataEntry CreateCoredataEntryFromAD(IDictionary properties, Dictionary<string, string> sAMAccountNameToCprMap, long maxPasswordAge)
        {
            string Guid = fetchGuid(properties);

            // Name logic
            string ChosenName = properties.GetValue<string>(propertyResolver.ChosenNameProperty, null);
            string Firstname = properties.GetValue<string>(propertyResolver.FirstnameProperty, null);
            string Surname = properties.GetValue<string>(propertyResolver.SurnameProperty, null);

            string Name = ChosenName;
            bool ForceUseCalculatedName = Settings.GetBooleanValue("ActiveDirectory.Property.Name.Calculated");
            if (ForceUseCalculatedName || string.IsNullOrEmpty(ChosenName))
            {
                Name = Firstname + " " + Surname;
            }

            // Disabled logic
            var accountControlValue = properties.GetValue<int>(propertyResolver.UserAccountControlProperty, 0);

            // AccountExpireProperty
            string accountExpireDate = null;
            try
            {
                if (properties is ResultPropertyCollection)
                {
                    ResultPropertyCollection rpc = (ResultPropertyCollection)properties;

                    long largeInt = (long)rpc[propertyResolver.AccountExpireProperty][0];
                    if (largeInt > 0 && largeInt < long.MaxValue)
                    {
                        var dateTime = DateTime.FromFileTime(largeInt);

                        accountExpireDate = dateTime.ToString("yyyy-MM-dd");
                    }
                }
                else if (properties is PropertyCollection)
                {
                    PropertyCollection pc = (PropertyCollection)properties;

                    // expire
                    var largeInt = (IAdsLargeInteger)pc[propertyResolver.AccountExpireProperty].Value;
                    var datelong = (largeInt.HighPart << 32) + largeInt.LowPart;

                    if (datelong > 0 && datelong < long.MaxValue)
                    {
                        var dateTime = DateTime.FromFileTime(datelong);

                        accountExpireDate = dateTime.ToString("yyyy-MM-dd");
                    }
                }
            }
            catch (Exception ex)
            {
                Logger.Warning("Unable to read accountExpire on: " + Name, ex);
            }

            // Create entry
            var user = new CoredataEntry()
            {
                Uuid = Guid,
                Name = Name,
                Email = properties.GetValue<string>(propertyResolver.EmailProperty, null),
                SamAccountName = properties.GetValue<string>(propertyResolver.SAMAccountNameProperty, null),
                Attributes = new Dictionary<string, string>(),
                Deleted = properties.GetValue<bool>(propertyResolver.DeletedProperty, false),
                Disabled = ((accountControlValue & AccountDisable) == AccountDisable),
                NSISAllowed = false,
                ExpireTimestamp = accountExpireDate,
                NextPasswordChange = GetNextPasswordChangeDate(properties, accountControlValue, maxPasswordAge),
                TransferToNemlogin = false
            };

            if (!string.IsNullOrEmpty(propertyResolver.ExternalMitIdUuidProperty))
            {
                user.ExternalNemloginUserUuid = properties.GetValue<string>(propertyResolver.ExternalMitIdUuidProperty, null);
            }

            if (!string.IsNullOrEmpty(propertyResolver.CprProperty))
            {
                user.Cpr = properties.GetValue<string>(propertyResolver.CprProperty, null);
            }

            // special case - if the users CPR is empty, but an externalNemLoginUuid is present, set the CPR to 0000000000,
            // so the user gets transfered (a cpr is required)
            if (string.IsNullOrEmpty(user.Cpr) && !string.IsNullOrEmpty(user.ExternalNemloginUserUuid))
            {
                user.Cpr = "0000000000";
            }

            // EAN
            if (!string.IsNullOrEmpty(propertyResolver.EanProperty))
            {
                user.Ean = properties.GetValue<string>(propertyResolver.EanProperty, null);
            }

            // defensive SQL fallback lookup
            if (user.Cpr == null)
            {
                if (sAMAccountNameToCprMap == null)
                {
                    sAMAccountNameToCprMap = SQLService.GetCpr(user.SamAccountName);
                }

                if (sAMAccountNameToCprMap != null && sAMAccountNameToCprMap.ContainsKey(user.SamAccountName.ToLower()))
                {
                    user.Cpr = sAMAccountNameToCprMap[user.SamAccountName.ToLower()];
                }
            }

            // Fill out SubDomain if configured (not really needed for full load, but needed for delta)
            string subDomain = Settings.GetStringValue("Backend.SubDomain");
            if (!string.IsNullOrEmpty(subDomain))
            {
                user.SubDomain = subDomain;
            }

            var dn = properties.GetValue<string>(propertyResolver.DistinguishedNameProperty, null);
            if (!string.IsNullOrEmpty(dn))
            {
                var tokens = dn.Split(',');
                if (tokens.Length >= 2)
                {
                    var departmentTokens = tokens[1].Split('=');
                    if (departmentTokens.Length >= 2)
                    {
                        user.Department = departmentTokens[1];
                    }
                }
            }

            // Fill out attributes Map with fetched values from AD
            Dictionary<string, string> attributes = Settings.GetStringValues("ActiveDirectory.Attributes.");
            if (attributes != null)
            {
                foreach (var attribute in attributes)
                {
                    string value = properties.GetValue<string>(attribute.Value, null);
                    if (value != null)
                    {
                        user.Attributes.Add(attribute.Key, value);
                    }
                }
            }

            return user;
        }

        private string fetchGuid(SearchResult searchResult)
        {
            try
            {
                // custom value handler
                if (!string.IsNullOrEmpty(propertyResolver.ObjectGuidProperty) && !"objectGUID".Equals(propertyResolver.ObjectGuidProperty))
                {
                    var nullableGUID = searchResult.Properties.GetValue<string>(propertyResolver.ObjectGuidProperty, null);

                    if (nullableGUID != null)
                    {
                        return new Guid(nullableGUID).ToString().ToLower();
                    }
                }
                else // objectGuid case
                {
                    var nullableGUID = searchResult.Properties.GetValue<System.Byte[]>(propertyResolver.ObjectGuidProperty, null);

                    if (nullableGUID != null)
                    {
                        return new Guid(nullableGUID).ToString().ToLower();
                    }
                }
            }
            catch (Exception ex)
            {
                Log.Debug("Failed to parse GUID", ex);
            }

            return null;
        }

        private string fetchGuid(IDictionary properties)
        {
            try
            {
                // custom value handler
                if (!string.IsNullOrEmpty(propertyResolver.ObjectGuidProperty) && !"objectGUID".Equals(propertyResolver.ObjectGuidProperty))
                {
                    var nullableGUID = properties.GetValue<string>(propertyResolver.ObjectGuidProperty, null);

                    if (nullableGUID != null)
                    {
                        return new Guid(nullableGUID).ToString().ToLower();
                    }
                }
                else
                {
                    var nullableGUID = properties.GetValue<System.Byte[]>(propertyResolver.ObjectGuidProperty, null);
                    if (nullableGUID != null)
                    {
                        return new Guid(nullableGUID).ToString().ToLower();
                    }
                }
            }
            catch (Exception ex)
            {
                Log.Debug("Failed to parse GUID", ex);
            }

            return null;
        }

        private string CreateFilter(params string[] filters)
        {
            var allFilters = filters.ToList();
            allFilters.Add("objectClass=user");
            allFilters.Add("!(objectclass=computer)");
            allFilters.Add(Settings.GetStringValue("ActiveDirectory.Filter"));

            return string.Format("(&{0})", string.Concat(allFilters.Where(x => !String.IsNullOrEmpty(x)).Select(x => '(' + x + ')').ToArray()));
        }

        private string CreateMembeshipFilter(params string[] filters)
        {
            var allFilters = filters.ToList();
            allFilters.Add("objectClass=user");
            allFilters.Add(Settings.GetStringValue("ActiveDirectory.Filter"));

            return string.Format("(&{0})", string.Concat(allFilters.Where(x => !String.IsNullOrEmpty(x)).Select(x => '(' + x + ')').ToArray()));
        }

        private string CreateGroupFilter(params string[] filters)
        {
            var allFilters = filters.ToList();
            allFilters.Add("objectClass=Group");

            return string.Format("(&{0})", string.Concat(allFilters.Where(x => !String.IsNullOrEmpty(x)).Select(x => '(' + x + ')').ToArray()));
        }

        private long GetMaxPasswordAge()
        {
            // if global value is set, return it
            if (maxPasswordAgeConfig > 0)
            {
                return maxPasswordAgeConfig;
            }

            using (var rootEntry = GenerateDirectoryEntry())
            {
                using (var mySearcher = new DirectorySearcher(rootEntry))
                {
                    string filter = "maxPwdAge=*";
                    mySearcher.Filter = filter;
                    using (var results = mySearcher.FindAll())
                    {
                        long maxDays = 0;

                        if (results.Count >= 1)
                        {
                            for (int i = 0; i < results.Count; i++)
                            {
                                Int64 maxPwdAge = (Int64)results[i].Properties["maxPwdAge"][0];
                                long maxDaysCandidate = maxPwdAge / -864000000000;

                                if (maxDaysCandidate > maxDays)
                                {
                                    maxDays = maxDaysCandidate;
                                }
                            }
                        }

                        return maxDays;
                    }
                }
            }
        }

        const int DontExpirePasssword = 65536;

        private string GetNextPasswordChangeDate(IDictionary properties, int accountControlValue, long maxPasswordAge)
        {
            try
            {
                if (maxPasswordAge > 0 && ((accountControlValue & DontExpirePasssword) != DontExpirePasssword))
                {
                    long lastChanged = -2;
                    if (properties is ResultPropertyCollection)
                    {
                        ResultPropertyCollection rpc = (ResultPropertyCollection)properties;

                        lastChanged = (long)rpc[propertyResolver.PwdLastSetProperty][0];
                    }
                    else if (properties is PropertyCollection)
                    {
                        PropertyCollection pc = (PropertyCollection)properties;

                        var largeInt = (IAdsLargeInteger)pc[propertyResolver.PwdLastSetProperty].Value;
                        lastChanged = (largeInt.HighPart << 32) + largeInt.LowPart;
                    }

                    if (lastChanged != -1)
                    {
                        long daysLeft = maxPasswordAge - DateTime.Today.Subtract(DateTime.FromFileTime(lastChanged)).Days;

                        return DateTime.Today.AddDays(daysLeft).ToString("yyyy-MM-dd");
                    }
                }
            }
            catch (Exception ex)
            {
                Log.Debug("Failed to find GetNextPasswordChangeDate", ex);
            }

            return null;
        }
    }
}
