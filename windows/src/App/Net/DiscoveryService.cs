using System.Collections.Concurrent;
using System.Net;
using System.Net.NetworkInformation;
using System.Net.Sockets;
using System.Text;

namespace CompartirArchivosRED.Net;

public sealed class Peer
{
    public string Id = "";
    public string Name = "";
    public string Platform = "";
    public IPAddress Address = IPAddress.None;
    public int Port;
    public DateTime LastSeen;

    public string Display => $"{Name}  ·  {Platform}  ·  {Address}";
}

/// <summary>Descubrimiento por UDP broadcast en la LAN (puerto 45454).</summary>
public sealed class DiscoveryService : IDisposable
{
    private readonly string _selfId;
    private readonly string _selfName;
    private UdpClient? _udp;
    private CancellationTokenSource? _cts;
    private readonly ConcurrentDictionary<string, Peer> _peers = new();

    public event Action? PeersChanged;

    public DiscoveryService(string selfId, string selfName)
    {
        _selfId = selfId;
        _selfName = selfName;
    }

    public IReadOnlyList<Peer> Peers =>
        _peers.Values.OrderBy(p => p.Name).ToList();

    public void Start()
    {
        _cts = new CancellationTokenSource();
        _udp = new UdpClient();
        _udp.Client.SetSocketOption(SocketOptionLevel.Socket, SocketOptionName.ReuseAddress, true);
        _udp.Client.Bind(new IPEndPoint(IPAddress.Any, Proto.DiscoveryPort));
        _udp.EnableBroadcast = true;

        var ct = _cts.Token;
        Task.Run(() => ListenLoop(ct));
        Task.Run(() => AnnounceLoop(ct));
        Task.Run(() => ExpireLoop(ct));
    }

    private async Task AnnounceLoop(CancellationToken ct)
    {
        var msg = new Msg
        {
            Type = Proto.ANNOUNCE,
            Id = _selfId,
            Name = _selfName,
            Platform = "windows",
            Port = Proto.TransferPort
        };
        byte[] data = Encoding.UTF8.GetBytes(msg.ToJson());

        while (!ct.IsCancellationRequested)
        {
            // Enviar a la dirección de broadcast de CADA interfaz activa.
            // En equipos con varias tarjetas (Wi-Fi + Ethernet + adaptadores
            // virtuales) el broadcast global 255.255.255.255 sale por una sola
            // interfaz —a menudo la equivocada—, por lo que otros dispositivos
            // no ven a este equipo. Emitir por cada subred lo soluciona.
            foreach (var ep in BroadcastTargets())
            {
                try { _udp!.Send(data, data.Length, ep); } catch { }
            }
            try { await Task.Delay(2000, ct); } catch { break; }
        }
    }

    private static List<IPEndPoint> BroadcastTargets()
    {
        var targets = new List<IPEndPoint>();
        try
        {
            foreach (var ni in NetworkInterface.GetAllNetworkInterfaces())
            {
                if (ni.OperationalStatus != OperationalStatus.Up) continue;
                if (ni.NetworkInterfaceType == NetworkInterfaceType.Loopback) continue;

                foreach (var ua in ni.GetIPProperties().UnicastAddresses)
                {
                    if (ua.Address.AddressFamily != AddressFamily.InterNetwork) continue;
                    var ip = ua.Address.GetAddressBytes();
                    var mask = ua.IPv4Mask?.GetAddressBytes();
                    if (mask == null || mask.Length != 4) continue;

                    var bcast = new byte[4];
                    for (int i = 0; i < 4; i++) bcast[i] = (byte)(ip[i] | (~mask[i] & 0xFF));
                    targets.Add(new IPEndPoint(new IPAddress(bcast), Proto.DiscoveryPort));
                }
            }
        }
        catch { }

        // Fallback: broadcast global limitado.
        targets.Add(new IPEndPoint(IPAddress.Broadcast, Proto.DiscoveryPort));
        return targets;
    }

    private async Task ListenLoop(CancellationToken ct)
    {
        while (!ct.IsCancellationRequested)
        {
            try
            {
                var result = await _udp!.ReceiveAsync(ct);
                var m = Msg.FromJson(Encoding.UTF8.GetString(result.Buffer));
                if (m == null || m.Type != Proto.ANNOUNCE || m.Id == null) continue;
                if (m.Id == _selfId) continue;

                bool isNew = !_peers.ContainsKey(m.Id);
                _peers[m.Id] = new Peer
                {
                    Id = m.Id,
                    Name = m.Name ?? "?",
                    Platform = m.Platform ?? "?",
                    Address = result.RemoteEndPoint.Address,
                    Port = m.Port ?? Proto.TransferPort,
                    LastSeen = DateTime.UtcNow
                };
                if (isNew) PeersChanged?.Invoke();
            }
            catch (OperationCanceledException) { break; }
            catch { }
        }
    }

    private async Task ExpireLoop(CancellationToken ct)
    {
        while (!ct.IsCancellationRequested)
        {
            try { await Task.Delay(3000, ct); } catch { break; }
            var now = DateTime.UtcNow;
            bool changed = false;
            foreach (var kv in _peers)
                if ((now - kv.Value.LastSeen).TotalSeconds > 10)
                    changed |= _peers.TryRemove(kv.Key, out _);
            if (changed) PeersChanged?.Invoke();
        }
    }

    public void Dispose()
    {
        try { _cts?.Cancel(); } catch { }
        try { _udp?.Dispose(); } catch { }
    }
}
