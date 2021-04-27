using Serilog;
using System;
using System.Collections;
using System.Collections.Generic;
using System.DirectoryServices;
using System.DirectoryServices.ActiveDirectory;
using System.Linq;

namespace OS2faktorADSync
{
    public class ActiveDirectoryService
    {
        const int AccountDisable = 2;

        public ILogger Logger { get; set; }
        private readonly PropertyResolver propertyResolver = new PropertyResolver();
        private readonly string nsisAllowedGroup;
        private readonly Boolean loadAllUsers;

        public ActiveDirectoryService()
        {
            nsisAllowedGroup = Settings.GetStringValue("ActiveDirectory.NSISAllowed.Group");
            loadAllUsers = Settings.GetBooleanValue("ActiveDirectory.loadAllUsers");
        }

        public IEnumerable<CoredataEntry> GetFullSyncUsers(out byte[] directorySynchronizationCookie)
        {
            using (var directoryEntry = GenerateDirectoryEntry())
            {
                Dictionary<string, CoredataEntry> result = new Dictionary<string, CoredataEntry>();

                if (loadAllUsers)
                {
                    // Full load
                    string filter = CreateFilter("!(isDeleted=TRUE)", propertyResolver.CprProperty + "=*");
                    using (var directorySearcher = new DirectorySearcher(directoryEntry, filter, propertyResolver.AllProperties, SearchScope.Subtree))
                    {
                        directorySearcher.DirectorySynchronization = new DirectorySynchronization(DirectorySynchronizationOptions.None);
                        using (var searchResultCollection = directorySearcher.FindAll())
                        {
                            Logger.Information("Found {0} users in Active Directory", searchResultCollection.Count);

                            foreach (SearchResult searchResult in searchResultCollection)
                            {
                                Logger.Verbose("Full sync searchResult: {@searchResult}", searchResult);

                                CoredataEntry user = CreateCoredataEntryFromAD(searchResult.Properties);
                                if (user.IsActive() && user.IsValid())
                                {
                                    result.Add(user.Uuid, user);
                                }
                            }

                            Logger.Information("{0} users where active and valid", result.Count);
                        }

                        directorySynchronizationCookie = directorySearcher.DirectorySynchronization.GetDirectorySynchronizationCookie();
                    }

                    // Additional search for members of a specific group (Recursive / transative)
                    filter = CreateFilter("!(isDeleted=TRUE)", propertyResolver.CprProperty + "=*", "memberOf:1.2.840.113556.1.4.1941:=" + nsisAllowedGroup);
                    using (var directorySearcher = new DirectorySearcher(directoryEntry, filter, propertyResolver.AllProperties, SearchScope.Subtree))
                    {
                        using (var searchResultCollection = directorySearcher.FindAll())
                        {
                            foreach (SearchResult searchResult in searchResultCollection)
                            {
                                string Guid = new Guid(searchResult.Properties.GetValue<System.Byte[]>(propertyResolver.ObjectGuidProperty, null)).ToString();
                                result[Guid].NSISAllowed = true;
                            }
                        }
                    }
                    return result.Values;
                }
                else
                {
                    // Only load nsis
                    string filter = CreateFilter("!(isDeleted=TRUE)", propertyResolver.CprProperty + "=*", "memberOf:1.2.840.113556.1.4.1941:=" + nsisAllowedGroup);
                    using (var directorySearcher = new DirectorySearcher(directoryEntry, filter, propertyResolver.AllProperties, SearchScope.Subtree))
                    {
                        directorySearcher.DirectorySynchronization = new DirectorySynchronization(DirectorySynchronizationOptions.None);
                        using (var searchResultCollection = directorySearcher.FindAll())
                        {
                            Logger.Information("Found {0} users in Active Directory", searchResultCollection.Count);

                            foreach (SearchResult searchResult in searchResultCollection)
                            {
                                Logger.Verbose("Full sync searchResult: {@searchResult}", searchResult);

                                CoredataEntry user = CreateCoredataEntryFromAD(searchResult.Properties);
                                user.NSISAllowed = true;

                                if (user.IsActive() && user.IsValid())
                                {
                                    result.Add(user.Uuid, user);
                                }
                            }
                        }

                        directorySynchronizationCookie = directorySearcher.DirectorySynchronization.GetDirectorySynchronizationCookie();
                    }

                    return result.Values;
                }
            }
        }

        public DeltaSync GetDeltaSyncUsers(ref byte[] directorySynchronizationCookie)
        {
            DeltaSync result = new DeltaSync();
            result.CreateEntries = new List<CoredataEntry>();
            result.DeleteEntries = new List<CoredataDeleteEntry>();

            using (var directoryEntry = GenerateDirectoryEntry())
            {
                string filter = CreateFilter("!(isDeleted=TRUE)", propertyResolver.CprProperty + "=*");

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
                                Logger.Information("Received delta sync user {0}", searchResult.Path);
                                //get properties from the directoryEntry because the delta search result only contain the changed properties

                                var coreDataEntry = CreateCoredataEntryFromAD(searchResult.GetDirectoryEntry().Properties);
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

                foreach (CoredataEntry entry in result.CreateEntries)
                {
                    filter = CreateFilter("!(isDeleted=TRUE)", propertyResolver.CprProperty + "=*", propertyResolver.SAMAccountNameProperty + "=" + entry.SamAccountName, "memberOf:1.2.840.113556.1.4.1941:=" + nsisAllowedGroup);
                    using (var directorySearcher = new DirectorySearcher(directoryEntry, filter, propertyResolver.AllProperties, SearchScope.Subtree))
                    {
                        using (var searchResultCollection = directorySearcher.FindAll())
                        {
                            if (searchResultCollection.Count == 1)
                            {
                                entry.NSISAllowed = true;
                            }
                        }
                    }
                }

                return result;
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

                    Logger.Verbose("Connected to " + controller.Name);
                    break;
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

        private CoredataEntry CreateCoredataEntryFromAD(IDictionary properties)
        {
            string Guid = new Guid(properties.GetValue<System.Byte[]>(propertyResolver.ObjectGuidProperty, null)).ToString();

            // Name logic
            string ChosenName = properties.GetValue<String>(propertyResolver.ChosenNameProperty, null);
            string Firstname = properties.GetValue<String>(propertyResolver.FirstnameProperty, null);
            string Surname = properties.GetValue<String>(propertyResolver.SurnameProperty, null);
            string Name = String.IsNullOrEmpty(ChosenName) ? Firstname + " " + Surname : ChosenName;

            // Disabled logic
            var accountControlValue = properties.GetValue<Int32>(propertyResolver.UserAccountControlProperty, 0);

            // Create entry
            var user = new CoredataEntry()
            {
                Uuid = Guid,
                Cpr = properties.GetValue<string>(propertyResolver.CprProperty, null),
                Name = Name,
                Email = properties.GetValue<string>(propertyResolver.EmailProperty, null),
                SamAccountName = properties.GetValue<string>(propertyResolver.SAMAccountNameProperty, null),
                Attributes = new Dictionary<string, string>(),
                Deleted = properties.GetValue<bool>(propertyResolver.DeletedProperty, false),
                Disabled = ((accountControlValue & AccountDisable) == AccountDisable),
                NSISAllowed = false
            };

            return user;
        }

        private string CreateFilter(params string[] filters)
        {
            var allFilters = filters.ToList();
            allFilters.Add("objectClass=user");
            allFilters.Add("!(objectclass=computer)");
            allFilters.Add(Settings.GetStringValue("ActiveDirectory.Filter"));

            return string.Format("(&{0})", string.Concat(allFilters.Where(x => !String.IsNullOrEmpty(x)).Select(x => '(' + x + ')').ToArray()));
        }
    }
}
