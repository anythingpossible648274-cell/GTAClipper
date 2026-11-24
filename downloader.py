import os

import yt_dlp


def download(url, out_dir, cb):
    """Download a video with yt-dlp. cb.onProgress(fraction) is called while downloading.

    Returns [file_path, title]. Uses a single pre-merged stream (no ffmpeg needed on device).
    """

    def hook(d):
        if d.get("status") == "downloading":
            total = d.get("total_bytes") or d.get("total_bytes_estimate") or 0
            if total:
                cb.onProgress(float(d.get("downloaded_bytes", 0)) / float(total))

    opts = {
        "format": "best[ext=mp4][height<=1080]/best[height<=1080]/best",
        "outtmpl": os.path.join(out_dir, "%(id)s.%(ext)s"),
        "noplaylist": True,
        "progress_hooks": [hook],
        "quiet": True,
        "no_warnings": True,
    }
    with yt_dlp.YoutubeDL(opts) as ydl:
        info = ydl.extract_info(url, download=True)
        path = ydl.prepare_filename(info)
        return [path, info.get("title") or ""]
