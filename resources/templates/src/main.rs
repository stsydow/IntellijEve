use tokio_core::reactor::Core;

mod task_graph;
mod nodes;
mod structs;
mod stream_copy;

pub fn main() -> ()
{
    let mut runtime = Core::new().unwrap();
    let task_graph = task_graph::build_graph();
    runtime.run(task_graph).unwrap();
}