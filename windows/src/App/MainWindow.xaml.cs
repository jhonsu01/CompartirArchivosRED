using System.Collections.ObjectModel;
using System.Diagnostics;
using System.IO;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Media;
using CompartirArchivosRED.Net;
using Microsoft.Win32;

namespace CompartirArchivosRED;

public partial class MainWindow : Window
{
    private readonly string _selfId;
    private readonly string _selfName = Environment.MachineName;
    private readonly DiscoveryService _discovery;
    private readonly TransferService _transfer;
    private readonly ObservableCollection<Peer> _peers = new();

    public MainWindow()
    {
        InitializeComponent();

        _selfId = LoadOrCreateId();
        _discovery = new DiscoveryService(_selfId, _selfName);
        _transfer = new TransferService(_selfName);

        SelfInfo.Text = $"Tu dispositivo: {_selfName}  ·  Descargas: {_transfer.DownloadFolder}";
        PeersList.ItemsSource = _peers;
        PeersList.SelectionChanged += (_, _) => SendBtn.IsEnabled = PeersList.SelectedItem != null;

        _discovery.PeersChanged += OnPeersChanged;
        _transfer.Log += AppendLog;
        _transfer.Progress += p => Dispatcher.Invoke(() => Progress.Value = p);
        _transfer.IncomingStarted += OnIncomingStarted;
        _transfer.IncomingFinished += OnIncomingFinished;

        Loaded += (_, _) =>
        {
            _transfer.StartServer();
            _discovery.Start();
            AppendLog("Listo. Buscando dispositivos en la red…");
        };
        Closed += (_, _) => { _discovery.Dispose(); _transfer.Dispose(); };
    }

    private void OnPeersChanged() => Dispatcher.Invoke(() =>
    {
        var current = _discovery.Peers;
        var selectedId = (PeersList.SelectedItem as Peer)?.Id;
        _peers.Clear();
        foreach (var p in current) _peers.Add(p);
        if (selectedId != null)
        {
            var again = _peers.FirstOrDefault(p => p.Id == selectedId);
            if (again != null) PeersList.SelectedItem = again;
        }
    });

    private void OnIncomingStarted(IncomingInfo info) => Dispatcher.Invoke(() =>
    {
        PinBannerTitle.Text = $"«{info.SenderName}» quiere enviarte {info.FileCount} archivo(s) " +
                              $"({TransferService.FormatSize(info.TotalSize)}).";
        PinBannerCode.Text = info.Pin;
        PinBanner.Visibility = Visibility.Visible;
    });

    private void OnIncomingFinished(bool ok) => Dispatcher.Invoke(() =>
    {
        PinBanner.Visibility = Visibility.Collapsed;
        AppendLog(ok ? "Recepción finalizada correctamente." : "Recepción cancelada o fallida.");
    });

    private async void SendBtn_Click(object sender, RoutedEventArgs e)
    {
        if (PeersList.SelectedItem is not Peer peer) return;

        var dlg = new OpenFileDialog { Multiselect = true, Title = "Selecciona archivos a enviar" };
        if (dlg.ShowDialog() != true) return;
        var files = dlg.FileNames;

        SendBtn.IsEnabled = false;
        try
        {
            Func<Task<string?>> pinProvider = () => Dispatcher.InvokeAsync(() => AskPin(peer.Name)).Task;
            await Task.Run(() => _transfer.SendFilesAsync(peer, files, pinProvider));
        }
        finally
        {
            SendBtn.IsEnabled = PeersList.SelectedItem != null;
        }
    }

    private void OpenFolderBtn_Click(object sender, RoutedEventArgs e)
    {
        try { Process.Start(new ProcessStartInfo(_transfer.DownloadFolder) { UseShellExecute = true }); }
        catch (Exception ex) { AppendLog("No se pudo abrir la carpeta: " + ex.Message); }
    }

    /// <summary>Diálogo modal para introducir el PIN mostrado en el receptor.</summary>
    private string? AskPin(string peerName)
    {
        var win = new Window
        {
            Title = "PIN de emparejamiento",
            Width = 380,
            Height = 210,
            ResizeMode = ResizeMode.NoResize,
            WindowStartupLocation = WindowStartupLocation.CenterOwner,
            Owner = this,
            Background = (Brush)FindResource("BgBrush")
        };

        var stack = new StackPanel { Margin = new Thickness(20) };
        stack.Children.Add(new TextBlock
        {
            Text = $"Introduce el PIN de 6 dígitos que aparece en «{peerName}»:",
            Foreground = (Brush)FindResource("TextBrush"),
            TextWrapping = TextWrapping.Wrap,
            Margin = new Thickness(0, 0, 0, 12)
        });

        var input = new TextBox
        {
            FontSize = 22,
            FontFamily = new FontFamily("Consolas"),
            MaxLength = 6,
            Padding = new Thickness(8),
            Background = (Brush)FindResource("SurfaceBrush"),
            Foreground = (Brush)FindResource("TextBrush"),
            BorderThickness = new Thickness(0)
        };
        stack.Children.Add(input);

        string? result = null;
        var buttons = new StackPanel
        {
            Orientation = Orientation.Horizontal,
            HorizontalAlignment = HorizontalAlignment.Right,
            Margin = new Thickness(0, 16, 0, 0)
        };
        var cancel = new Button { Content = "Cancelar", Margin = new Thickness(0, 0, 10, 0), MinWidth = 90 };
        var ok = new Button { Content = "Aceptar", MinWidth = 90, IsDefault = true };
        cancel.Background = (Brush)FindResource("SurfaceAltBrush");
        cancel.Foreground = (Brush)FindResource("TextBrush");
        cancel.Click += (_, _) => { win.DialogResult = false; };
        ok.Click += (_, _) => { result = input.Text; win.DialogResult = true; };
        buttons.Children.Add(cancel);
        buttons.Children.Add(ok);
        stack.Children.Add(buttons);

        win.Content = stack;
        input.Focus();
        return win.ShowDialog() == true ? result : null;
    }

    private void AppendLog(string line) => Dispatcher.Invoke(() =>
    {
        LogBox.AppendText($"[{DateTime.Now:HH:mm:ss}] {line}{Environment.NewLine}");
        LogBox.ScrollToEnd();
    });

    private static string LoadOrCreateId()
    {
        try
        {
            string dir = Path.Combine(
                Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData),
                "CompartirArchivosRED");
            Directory.CreateDirectory(dir);
            string file = Path.Combine(dir, "device-id.txt");
            if (File.Exists(file))
            {
                string existing = File.ReadAllText(file).Trim();
                if (!string.IsNullOrWhiteSpace(existing)) return existing;
            }
            string id = Guid.NewGuid().ToString("N");
            File.WriteAllText(file, id);
            return id;
        }
        catch
        {
            return Guid.NewGuid().ToString("N");
        }
    }
}
