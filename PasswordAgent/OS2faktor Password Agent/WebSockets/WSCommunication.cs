using Newtonsoft.Json;
using Serilog;
using System;
using System.Dynamic;
using WebSocketSharp;

namespace OS2faktor_Password_Agent
{
    public class WSCommunication
    {
        private static string VERSION = "1.0.0";

        private WebSocket socket;

        public ADStub adStub { get; set; }

        public ILogger Logger { get; set; }

        public WSCommunication()
        {
            socket = new WebSocket(Settings.GetStringValue("webSocketUrl"));
        }

        internal void Connect()
        {
            if (!socket.IsAlive)
            {
                Logger.Information("Attempting to connect");
                socket.Connect();
            }
        }

        internal void Disconnect()
        {
            socket.Close();
        }

        public void Init()
        {
            socket.OnMessage += (sender, e) =>
            {
                if (e.IsText)
                {
                    try
                    {
                        dynamic message = JsonConvert.DeserializeObject(e.Data);

                        LogRequest(message);

                        if (!ValidateMessage(message))
                        {
                            Logger.Error("Invalid signature on message: " + message.transactionUuid);
                            return;
                        }

                        string command = (string)message.command;
                        switch (command)
                        {
                            case "AUTHENTICATE":
                                Reply((string)message.transactionUuid, (string)message.command, Settings.GetStringValue("domain"), true);
                                break;
                            case "VALIDATE_PASSWORD":
                                if (Settings.GetBooleanValue("allowValidatePassword"))
                                {
                                    Reply((string)message.transactionUuid, (string)message.command, (string)message.target, adStub.ValidatePassword((string)message.target, (string)message.payload));
                                }
                                else
                                {
                                    Reply((string)message.transactionUuid, (string)message.command, (string)message.target, false);
                                }
                                break;
                            case "SET_PASSWORD":
                                if (Settings.GetBooleanValue("allowChangePassword"))
                                {
                                    Reply((string)message.transactionUuid, (string)message.command, (string)message.target, adStub.ChangePassword((string)message.target, (string)message.payload));
                                }
                                else
                                {
                                    Reply((string)message.transactionUuid, (string)message.command, (string)message.target, false);
                                }
                                break;
                            default:
                                Logger.Error("Unknown request: " + message.command);
                                break;
                        }
                    }
                    catch (Exception ex)
                    {
                        Logger.Error(ex, "Get error on request");
                    }
                }
            };

            socket.OnClose += (sender, e) =>
            {
                Logger.Information("Connection closed by other side");
            };

            Connect();
        }

        private bool ValidateMessage(dynamic message)
        {
            string command = (string)message.command;

            switch (command)
            {
                case "AUTHENTICATE":
                    {
                        string mac = HMacUtil.Encode(((string)message.transactionUuid + "." + (string)message.command));
                        return Equals(mac, (string)message.signature);
                    }
                case "VALIDATE_PASSWORD":
                case "SET_PASSWORD":
                    {
                        string mac = HMacUtil.Encode(((string)message.transactionUuid + "." + (string)message.command + "." + (string)message.target + "." + (string)message.payload));
                        return Equals(mac, (string)message.signature);
                    }
                default:
                    Logger.Information("Unknown command: " + (string) message.command);
                    break;
            }

            return false;
        }

        internal void Reply(string transactionUuid, string command, string target, bool valid)
        {
            dynamic response = new ExpandoObject();
            response.transactionUuid = transactionUuid;
            response.command = command;
            response.target = target;
            response.status = (valid) ? "true" : "false";
            response.clientVersion = VERSION;
            response.signature = HMacUtil.Encode(transactionUuid + "." + command + "." + target + "." + (valid ? "true" : "false"));

            socket.Send(JsonConvert.SerializeObject(response));

            LogResponse(response);
        }

        private void LogResponse(dynamic response)
        {
            Logger.Information("Response for " + response.command + " (" + response.transactionUuid + "): target=" + response.target + ", result=" + response.status);
        }

        private void LogRequest(dynamic request)
        {
            if ("AUTHENTICATE".Equals(request.command))
            {
                Logger.Information("Request for " + request.command + " (" + request.transactionUuid + ")");
            }
            else
            {
                Logger.Information("Request for " + request.command + " (" + request.transactionUuid + "): target=" + request.target);
            }
        }
    }
}
