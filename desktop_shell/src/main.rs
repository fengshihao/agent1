//! 单窗 WebView 薄壳：仅加载 `http://127.0.0.1:<port>/`（由 Java `UiHttpServer` 提供）。
//! 用法：`agent1-ui-window <url>`（例如 `http://127.0.0.1:17831/`）

use tao::{
    event::{Event, StartCause, WindowEvent},
    event_loop::{ControlFlow, EventLoop},
    window::WindowBuilder,
};
use wry::WebViewBuilder;

fn main() -> wry::Result<()> {
    let url = std::env::args()
        .nth(1)
        .filter(|s| !s.is_empty())
        .unwrap_or_else(|| {
            eprintln!("用法: agent1-ui-window <url>");
            eprintln!("示例: agent1-ui-window http://127.0.0.1:17831/");
            std::process::exit(2);
        });

    if !url.starts_with("http://127.0.0.1:") && !url.starts_with("http://localhost:") {
        eprintln!("警告: 仅建议加载本机 UI 服务地址 (127.0.0.1 / localhost)，当前: {}", url);
    }

    let event_loop = EventLoop::new();
    let window = WindowBuilder::new()
        .with_title("Agent1")
        .build(&event_loop)
        .expect("create window");

    let builder = WebViewBuilder::new().with_url(&url);

    #[cfg(not(target_os = "linux"))]
    let _webview = builder.build(&window)?;

    #[cfg(target_os = "linux")]
    let _webview = {
        use wry::WebViewBuilderExtUnix;
        builder.build_gtk(window.gtk_window())?
    };

    event_loop.run(move |event, _, control_flow| {
        *control_flow = ControlFlow::Wait;

        match event {
            Event::NewEvents(StartCause::Init) => {}
            Event::WindowEvent {
                event: WindowEvent::CloseRequested,
                ..
            } => *control_flow = ControlFlow::Exit,
            _ => (),
        }
    });

    Ok(())
}
