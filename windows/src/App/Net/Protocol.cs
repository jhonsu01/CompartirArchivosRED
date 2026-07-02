using System.Text.Json;
using System.Text.Json.Serialization;

namespace CompartirArchivosRED.Net;

/// <summary>Constantes del protocolo común (ver shared/protocol/PROTOCOL.md).</summary>
public static class Proto
{
    public const int Version = 1;
    public const int DiscoveryPort = 45454;
    public const int TransferPort = 45455;

    public const string ANNOUNCE = "ANNOUNCE";
    public const string OFFER = "OFFER";
    public const string PIN_REQUIRED = "PIN_REQUIRED";
    public const string PIN = "PIN";
    public const string ACCEPTED = "ACCEPTED";
    public const string REJECTED = "REJECTED";
    public const string FILE = "FILE";
    public const string DONE = "DONE";
}

/// <summary>Mensaje de control JSON (delimitado por salto de línea en TCP).</summary>
public sealed class Msg
{
    [JsonPropertyName("v")] public int V { get; set; } = Proto.Version;
    [JsonPropertyName("type")] public string Type { get; set; } = "";
    [JsonPropertyName("id")] public string? Id { get; set; }
    [JsonPropertyName("name")] public string? Name { get; set; }
    [JsonPropertyName("platform")] public string? Platform { get; set; }
    [JsonPropertyName("port")] public int? Port { get; set; }
    [JsonPropertyName("pin")] public string? Pin { get; set; }
    [JsonPropertyName("reason")] public string? Reason { get; set; }
    [JsonPropertyName("fileCount")] public int? FileCount { get; set; }
    [JsonPropertyName("totalSize")] public long? TotalSize { get; set; }
    [JsonPropertyName("size")] public long? Size { get; set; }

    private static readonly JsonSerializerOptions Opts = new()
    {
        DefaultIgnoreCondition = JsonIgnoreCondition.WhenWritingNull
    };

    public string ToJson() => JsonSerializer.Serialize(this, Opts);

    public static Msg? FromJson(string s)
    {
        try { return JsonSerializer.Deserialize<Msg>(s); }
        catch { return null; }
    }
}
