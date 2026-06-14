# Poll SMTC once and write JSON to stdout
try {
    $null = [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager, Windows.Media, ContentType=WindowsRuntime]
    $mgr = [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager]::RequestAsync().GetAwaiter().GetResult()
    $session = $mgr.GetCurrentSession()

    if ($null -eq $session) { Write-Output '{"state":"Idle"}'; exit 0 }

    $playback = $session.GetPlaybackInfo()
    if ($playback.PlaybackStatus.ToString() -ne 'Playing') { Write-Output '{"state":"Idle"}'; exit 0 }

    $media    = $session.TryGetMediaPropertiesAsync().GetAwaiter().GetResult()
    $timeline = $session.GetTimelineProperties()
    $appId    = $session.SourceAppUserModelId

    $albumArtPath = $null
    try {
        $thumb = $media.Thumbnail
        if ($null -ne $thumb) {
            $dest   = [System.IO.Path]::Combine([System.IO.Path]::GetTempPath(), 'taby_albumart.jpg')
            $ras    = $thumb.OpenReadAsync().GetAwaiter().GetResult()
            $stream = $ras.AsStream()
            $fs     = [System.IO.File]::OpenWrite($dest)
            $stream.CopyTo($fs)
            $fs.Close(); $stream.Dispose()
            $albumArtPath = $dest
        }
    } catch { }

    [ordered]@{
        state        = 'Playing'
        title        = $media.Title
        artist       = $media.Artist
        appId        = $appId
        positionMs   = [long]($timeline.Position.TotalMilliseconds)
        durationMs   = [long]($timeline.EndTime.TotalMilliseconds)
        albumArtPath = $albumArtPath
    } | ConvertTo-Json -Compress
} catch {
    Write-Output '{"state":"Idle"}'
}
