package codegen

import editor.Edge
import editor.Node
import editor.Port
import editor.RootNode
import java.nio.file.Files
import java.nio.file.Paths;


class RustCodeGenerator {
    fun generateSkeleton(outputDirectory: String) {
        if (!Files.exists(Paths.get(outputDirectory + "/src"))) {
            Files.createDirectories(Paths.get(outputDirectory + "/src"))
        }
        if (!Files.exists(Paths.get(outputDirectory + "/src/eveamcp"))) {
            Files.createDirectories(Paths.get(outputDirectory + "/src/eveamcp"))
        }
        if (!Files.exists(Paths.get(outputDirectory + "/src/main.rs"))) {
            generateMainRS(outputDirectory)
        }
        if (!Files.exists(Paths.get(outputDirectory + "/src/structs.rs"))) {
            generateStructsRS(outputDirectory)
        }
        generateEVEaMCPRS(getEveamcpModRs(), outputDirectory)
    }

    fun generateCode(rootGraph: RootNode, outputDirectory: String) {
        println("[RustCodeGenerator] generating code...")
        if (!Files.exists(Paths.get(outputDirectory + "/src"))) {
            Files.createDirectories(Paths.get(outputDirectory + "/src"))
        }
        if (!Files.exists(Paths.get(outputDirectory + "/src/nodes"))) {
            Files.createDirectories(Paths.get(outputDirectory + "/src/nodes"))
        }
        val graphs: MutableList<Node> = getChildGraphs(rootGraph)
        generateInitRS(getInitRs(rootGraph, graphs), outputDirectory)
        generateNodesModRS(getNodesModRs(rootGraph, graphs), outputDirectory)
        generateEvethreadRs(getEvethreadRs(rootGraph, graphs), outputDirectory)
    }

    private fun getChildGraphs(node: Node): MutableList<Node> {
        val list = mutableListOf<Node>()
        println("adding node ${node.name}")
        list.add(node)
        node.childNodes.forEach {
            list.addAll(getChildGraphs(it))
        }
        return list
    }

    private fun generateInitRS(content: String, outputDirectory: String) {
        Files.write(Paths.get(outputDirectory + "/src/init.rs"), content.toByteArray())
    }

    private fun generateNodesModRS(content: String, baseDir: String) {
        Files.write(Paths.get(baseDir + "/src/nodes/mod.rs"), content.toByteArray())
    }

    private fun generateMainRS(outputDirectory: String) {
        val builder = StringBuilder()
        builder.append("extern crate csv;\n" +
                "extern crate rustc_serialize;\n" +
                "\n" +
                "mod nodes;\n" +
                "mod structs;\n" +
                "mod init;\n" +
                "mod eveamcp;\n" +
                "\n" +
                "use std::thread;\n" +
                "use std::time::Instant;\n" +
                "\n" +
                "\n" +
                "fn main() {\n" +
                "    println!(\"Hello, world!\");\n" +
                "    let start = Instant::now();\n" +
                "    let threads: Vec<thread::JoinHandle<()>> = init::init();\n" +
                "    for thread in threads {\n" +
                "        thread.join().unwrap();;\n" +
                "    }\n" +
                "    let elapsed = start.elapsed();\n" +
                "    println!(\"{:?}\", elapsed);\n" +
                "}\n")
        println("generating main.rs")
        Files.write(Paths.get(outputDirectory + "/src/main.rs"), builder.toString().toByteArray())
    }

    private fun generateStructsRS(outputDirectory: String) {
        Files.write(Paths.get(outputDirectory + "/src/structs.rs"), "".toByteArray())
    }

    private fun generateEVEaMCPRS(content: String, outputDirectory: String) {
        Files.write(Paths.get(outputDirectory + "/src/eveamcp/mod.rs"), content.toByteArray())
    }

    private fun generateEvethreadRs(content: String, outputDirectory: String) {
        Files.write(Paths.get(outputDirectory + "/src/eveamcp/evethread.rs"), content.toByteArray())
    }

    fun getEveamcpModRs(): String {
        var s =
                """use std::vec::Vec;
use std::sync::mpsc::Sender;
use std::sync::mpsc::Receiver;
use std::sync::mpsc::TryRecvError;

pub mod evethread;

/// An OutgoingPort is a port capable of sending data to [IncomingPorts](struct.IncomingPort.html)
///
/// The OutgoingPort owns a [SuccessorInstanceList](struct.SuccessorInstanceList.html) for every successor node, of whom each will receive the data sent through this port.
pub struct OutgoingPort<T> {
    pub successors: Vec<SuccessorInstanceList<T>>,
    /// TODO move this to the instance lists
    pub idx: usize
}

/// A SuccessorInstanceList is a container of senders to instances of the same successor
///
/// The SuccessorInstanceList owns a sender for every instance of a particular successor. Outgoing data is sent to one of them.
pub struct SuccessorInstanceList<T> {
    pub senders: Vec<Sender<T>>
}

/// Sends data to one instance of every successor node
///
/// # Arguments
/// * `t` - The data to be sent.
impl <T: Clone> OutgoingPort<T> {
    #[allow(dead_code)]
    pub fn send(&mut self, t: T) {
        let ref targets_vector = self.successors;
        match targets_vector.len() {
            0 => {
                panic!();
            },
            1 => {
                let ref target = targets_vector[0];
                self.idx %= target.senders.len();
                target.senders[self.idx].send(t.clone()).unwrap();
                self.idx += 1;
            },
            _ => {
                for target in targets_vector {
                    match target.senders.len() {
                        0 => {
                            panic!();
                        },
                        1 => {
                            target.senders[0].send(t.clone()).unwrap();
                        },
                        _ => {
                            self.idx %= target.senders.len();
                            target.senders[self.idx].send(t.clone()).unwrap();
                            self.idx += 1;
                        }
                    }
                }
            }
        }
    }
}

/// An IncomingPort is a port capable of receiving data sent from [OutgoingPorts](struct.OutgoingPort.html)
///
/// The IncomingPort of an instance owns the respective receiver of senders belonging to every instance of every predecessor node.
pub struct IncomingPort<T> {
    pub receiver: Receiver<T>
}
impl<T> IncomingPort<T> {
    /// Tries to receive data from the receiver
    ///
    /// Returns the result from the underlying receiver's `try_recv()`.
    #[allow(dead_code)]
    pub fn try_receive(&self) -> Result<T, TryRecvError> {
        return self.receiver.try_recv();
    }
}
"""
        return s;
    }

    fun getInitRs(rootGraph: RootNode, graphs: Collection<Node>): String {
        val builder = StringBuilder();
        builder.append(
                """use std::collections::HashMap;
use std::sync::Arc;
use std::sync::Mutex;
use std::sync::RwLock;
use std::sync::mpsc::Sender;
use std::sync::mpsc::Receiver;
use std::sync::mpsc::channel;
use std::thread::JoinHandle;

use eveamcp::evethread::EveThread;
use eveamcp::IncomingPort;
use eveamcp::OutgoingPort;
use eveamcp::SuccessorInstanceList;
use nodes::Graph;
use nodes::NormalNodeSet;
use nodes::SourceNodeSet;
use nodes::NormalNode;
use nodes::SourceNode;

""");
        graphs.forEach {
            if (it.childNodes.count() == 0) {
                builder.append(
                        """
use nodes::${it.name}Instance;"""
                );
            }
        }

        builder.append(
                """

pub fn startup() -> Vec<JoinHandle<()>> {
    let graph = build_model();
    let mut handles = vec!();
    let (source_nodes, normal_nodes) = build_initial_instances(&graph);
    let eve_threads = build_threads(&source_nodes, &normal_nodes);

    for thread in eve_threads {
        handles.push(thread.start(graph.clone()));
    }
    handles
}

fn build_model() -> Arc<RwLock<Graph>> {""");
        graphs.forEach {
            if (it.childNodes.count() == 0) {
                if (isSourceNode(it)) {
                    builder.append(
                            """
    let ${it.id} = Arc::new(Mutex::new(SourceNodeSet{idx: 0, instances: HashMap::new()}));"""
                    );
                } else {
                    builder.append(
                            """
    let ${it.id} = Arc::new(Mutex::new(NormalNodeSet{idx: 0, instances: HashMap::new()}));"""
                    );
                }
            }
        }
        builder.append(
                """
    Arc::new(RwLock::new((Graph {""");
        graphs.forEach {
            if (it.childNodes.count() == 0) {
                builder.append("""
        ${it.id}: ${it.id},""");
            }
        }
        builder.append(
                """
    })))
}

fn build_initial_instances(graph: &Arc<RwLock<Graph>>) -> (Vec<Arc<Mutex<SourceNode>>>, Vec<Arc<Mutex<NormalNode>>>) {
    let ref _graph: Graph = *graph.read().unwrap();
    let mut source_nodes: Vec<Arc<Mutex<SourceNode>>> = vec!();
    let mut normal_nodes: Vec<Arc<Mutex<NormalNode>>> = vec!();
""");
        graphs.forEach {
            if (it.childNodes.count() == 0 && !isSourceNode(it)) {
                builder.append(
                        """
    let (${it.id}_sender, ${it.id}_receiver): (Sender<u32>, Receiver<u32>) = channel();""");
            }
        }
        builder.append(
                """
""");
        graphs.forEach {
            if (it.childNodes.count() == 0) {
                builder.append(
                        """
    let ${it.id}_instance: Arc<Mutex<${it.name}Instance>> = Arc::new(Mutex::new(${it.name}Instance {
        instance_id: 0,""");
                it.out_ports.forEach {
                    builder.append(
                            """
        port_${it.id}: OutgoingPort {
            idx: 0,
            successors: vec!(""");
                    val edges = getOutgoingEdges(it)
                    edges.forEach {
                        builder.append("""
                SuccessorInstanceList {
                    senders: vec!(
                        ${it.target.parent!!.id}_sender.clone()
                    )
                },""");
                    }
                    builder.append(
                            """
            ),
        },""");
                }
                if (!isSourceNode(it)) {
                    builder.append(
                            """
        incoming_port: IncomingPort { receiver: ${it.id}_receiver },
        event: None,""");
                }
                builder.append("""
        instance_storage: None
    }));
""")
            }
        }
        graphs.forEach {
            if (it.childNodes.count() == 0) {
                builder.append(
                        """
    let ref mut ${it.id}_set = *_graph.${it.id}.lock().unwrap();""");
            }
        }
        builder.append("\n")
        graphs.forEach {
            if (it.childNodes.count() == 0) {
                builder.append(
                        """
    ${it.id}_set.instances.insert(0, ${it.id}_instance.clone());""");
            }
        }
        builder.append("\n")
        graphs.forEach {
            if (it.childNodes.count() == 0) {
                if (isSourceNode(it)) {
                    builder.append(
                            """
    source_nodes.push(${it.id}_instance);""");
                } else {
                    builder.append(
                            """
    normal_nodes.push(${it.id}_instance);""");
                }
            }

        }

        builder.append(
                    """

    (source_nodes, normal_nodes)
}

fn build_threads(source_nodes: &Vec<Arc<Mutex<SourceNode>>>, normal_nodes: &Vec<Arc<Mutex<NormalNode>>>) -> Vec<EveThread> {
    let mut threads = vec!();

    let mut t1 = EveThread { source_nodes: vec!(), normal_nodes: vec!(), cpu_id: 0};

    for source_node in source_nodes {
        t1.add_source(source_node.clone());
    }
    for normal_node in normal_nodes {
        t1.add_normal(normal_node.clone());
    }

    threads.push(t1);
    threads
}
""");
        return builder.toString();
    }

    fun getNodesModRs(rootGraph: RootNode, graphs: Collection<Node>): String {
        val builder = StringBuilder()
        graphs.forEach {
            if (it.childNodes.count() == 0) {
                builder.append(
                        """pub mod node_${it.name.toLowerCase()};
"""
                )
            }
        }
        builder.append(
                """
use std::any::Any;
use std::collections::HashMap;
use std::sync::Arc;
use std::sync::Mutex;
use std::sync::mpsc::TryRecvError;

use eveamcp::OutgoingPort;
use eveamcp::IncomingPort;""");
        graphs.forEach {
            if (it.childNodes.count() == 0) {
                builder.append("""
use nodes::node_${it.name.toLowerCase()}::${it.name}InstanceStorage;""");
            }
        }
        builder.append("""
#[allow(unused_imports)] use structs::*;

pub trait Node: Send {
    fn get_instance_id(&self) -> u64;
    fn get_model_id(&self) -> &str;
    fn as_any(&mut self) -> &mut Any;
    fn get_type(&self) -> NodeType;
}
pub trait SourceNode: Node {
    fn tick(&mut self) -> bool;
    fn init(&mut self);
}
pub trait NormalNodeInternal: Node {
    fn try_recv(&mut self) -> Status;
}
pub trait NormalNode: NormalNodeInternal {
    fn handle_event(&mut self);
    fn init(&mut self);
}

pub struct Graph {""");
        graphs.forEach {
            if(it.childNodes.count() == 0) {
                if (isSourceNode(it)) {
                    builder.append("""
    pub ${it.id}: Arc<Mutex<SourceNodeSet>>,""");
                } else {
                    builder.append("""
    pub ${it.id}: Arc<Mutex<NormalNodeSet>>,""");
                }
            }
        }
        builder.append(
                """
}
pub struct SourceNodeSet {
    pub instances: HashMap<u64, Arc<Mutex<SourceNode>>>,
    pub idx: u64
}
pub struct NormalNodeSet {
    pub instances: HashMap<u64, Arc<Mutex<NormalNode>>>,
    pub idx: u64
}

pub enum NodeType {
    Source,
    Normal
}
pub enum Status {
    Awaiting,
    Empty,
    Finished
}
""");
        graphs.forEach {
            if(it.childNodes.count() == 0) {
                if(isSourceNode(it)) {
                    builder.append(
                            """
pub struct ${it.name}Instance {
    pub instance_id: u64,""");
                    it.out_ports.forEach {
                        builder.append("""
    pub port_${it.id}: OutgoingPort<u32>,""");
                    }
                    builder.append(
                            """
    pub instance_storage: Option<${it.name}InstanceStorage>
}

impl Node for ${it.name}Instance {
    fn get_instance_id(&self) -> u64 {
        self.instance_id
    }
    fn get_model_id(&self) -> &str {
        &"${it.id}"
    }
    fn as_any(&mut self) -> &mut Any {
        self
    }
    fn get_type(&self) -> NodeType { NodeType::Source }
}""");
                } else {
                    builder.append(
                            """
pub struct ${it.name}Instance {
    pub instance_id: u64,
    pub event: Option<u32>,""");
                    it.out_ports.forEach {
                        builder.append("""
    pub port_${it.id}: OutgoingPort<u32>,""");
                    }
                    if (!isSourceNode(it)) {
                        builder.append("""
    pub incoming_port: IncomingPort<u32>,""");
                    }
                    builder.append(
                            """
    pub instance_storage: Option<${it.name}InstanceStorage>
}

impl Node for ${it.name}Instance {
    fn get_instance_id(&self) -> u64 {
        self.instance_id
    }
    fn get_model_id(&self) -> &str {
        &"${it.id}"
    }
    fn as_any(&mut self) -> &mut Any {
        self
    }
    fn get_type(&self) -> NodeType { NodeType::Normal }
}
impl NormalNodeInternal for ${it.name}Instance {
    fn try_recv(&mut self) -> Status {
        match self.incoming_port.try_receive() {
            Ok(e) => {
                self.event = Some(e);
                Status::Awaiting
            },
            Err(TryRecvError::Empty) => Status::Empty,
            Err(TryRecvError::Disconnected) => Status::Finished
        }
    }
}""");
                }

            }
        }

        //TODO add create methods
        return builder.toString();
    }

    fun getEvethreadRs(rootGraph: RootNode, graphs: Collection<Node>): String {
        var builder = StringBuilder();
        builder.append(
                """use std::sync::Arc;
use std::sync::Mutex;
use std::sync::RwLock;
use std::thread;
use std::thread::JoinHandle;

use nodes::Graph;
use nodes::NormalNode;
use nodes::SourceNode;
use nodes::Status;

/// An EveThread is a processing object that can handle multiple node instances
///
/// The EveThread owns Arc-Mutexes of its assigned instances. It executes the instances' ticks, and automatically removes stale instances from its pool.
#[allow(dead_code)]
pub struct EveThread {
    pub cpu_id: usize,
    pub source_nodes: Vec<Arc<Mutex<SourceNode>>>,
    pub normal_nodes: Vec<Arc<Mutex<NormalNode>>>
}
impl Drop for EveThread {
    fn drop(&mut self) {
        println!("dropping EveThread {}", self.cpu_id)
    }
}
impl EveThread {
    #[allow(dead_code)]
    pub fn add_source(&mut self, n: Arc<Mutex<SourceNode>>) {
        self.source_nodes.push(n);
    }
    #[allow(dead_code)]
    pub fn add_normal(&mut self, n: Arc<Mutex<NormalNode>>) {
        self.normal_nodes.push(n);
    }

    #[allow(dead_code)]
    pub fn start(mut self, graph: Arc<RwLock<Graph>>) -> JoinHandle<()>   {
        println!("EveThread starting");
        thread::spawn(move || {
            for node in self.source_nodes.as_mut_slice() {
                (*node.lock().unwrap()).init();
            }
            self.run(graph);
        })
    }

    pub fn tick_source(&mut self, i: usize, g:&Arc<RwLock<Graph>>) -> bool {
        let mut node = self.source_nodes[i].lock().unwrap();
        let retain = node.tick();
        if !retain {
            let i_id = node.get_instance_id();
            let m_id = node.get_model_id();
            remove_instance(i_id, m_id, g);
        }
        retain
    }

    pub fn tick_normal(&mut self, i: usize, g:&Arc<RwLock<Graph>>) -> bool {
        let retain;
        let mut node = self.normal_nodes[i].lock().unwrap();
        match node.try_recv() {
            Status::Awaiting => {
                node.handle_event();
                retain = true
            },
            Status::Empty => {
                retain = true
            },
            _ => {
                let i_id = node.get_instance_id();
                let m_id = node.get_model_id();
                remove_instance(i_id, m_id, g);
                retain = false;
            }
        }

        retain
    }

    /// Ticks all source node instances that belong to this EveThread
    ///
    /// Neither the graph write lock nor any instance locks must be held when calling this function.
    /// Stale instances are removed, and all references to them are dropped.
    pub fn run_sources(&mut self, graph: &Arc<RwLock<Graph>>) -> bool {
        let len = self.source_nodes.len();
        if len == 0 {
            return false
        }
        let mut del = 0;
        {
            for i in 0..len {
                if !self.tick_source(i, graph) {
                    del += 1;
                } else if del > 0 {
                    self.source_nodes.swap(i - del, i);
                }
            }
        }
        if del > 0 {
            self.source_nodes.truncate(len - del);
        }
        true
    }

    /// Ticks all normal node instances that belong to this EveThread
    ///
    /// Neither the graph write lock nor any instance locks must be held when calling this function.
    /// Stale instances are removed, and all references to them are dropped.
    pub fn run_normals(&mut self, graph: &Arc<RwLock<Graph>>) -> bool {
        let len = self.normal_nodes.len();
        if len == 0 {
            return false
        }
        let mut del = 0;
        {
            for i in 0..len {
                if !self.tick_normal(i, graph) {
                    del += 1;
                } else if del > 0 {
                    self.normal_nodes.swap(i - del, i);
                }
            }
        }
        if del > 0 {
            self.normal_nodes.truncate(len - del);
        }
        true
    }

    pub fn run(mut self, graph: Arc<RwLock<Graph>>) {
        loop {
            let source_running = self.run_sources(&graph);
            let normal_running = self.run_normals(&graph);
            if !source_running && !normal_running {
                println!("evethread has no more work");
                break;
            }
        }
    }
}

/// Removes an instance from the Graph
///
/// The NodeSet lock must not be held when calling this function.
fn remove_instance(i_id: u64, m_id: &str, g: &Arc<RwLock<Graph>>) {
    println!("remove instance {} of node {}", i_id, m_id);
    match m_id {""");
        graphs.forEach {
            if(it.childNodes.count() == 0) {
                builder.append(
                        """
        "${it.id}" => {
            let g: &Graph = &*g.read().unwrap();
            let ref mut s = *g.${it.id}.lock().unwrap();
            s.instances.remove(&i_id);
        },""");
            }
        }
        builder.append(
                """
        x => panic!("unknown instance {}", x)
    };
}
""");
        return builder.toString();
    }



    fun isSourceNode(g: Node): Boolean {
        return getIncomingEdges(g.in_port).count() == 0
    }

    fun getOutgoingEdges(p: Port): List<Edge> {
        val list: MutableList<Edge> = mutableListOf()
        val parent: Node = p.parent!!
        parent.childEdges.forEach {
            if (it.source == p) {
                list.add(it)
            }
        }
        val gparent:Node = p.parent.parent!!
        gparent.childEdges.forEach {
            if (it.source == p) {
                list.add(it)
            }
        }
        return list
    }

    fun getIncomingEdges(p: Port): List<Edge> {
        val list: MutableList<Edge> = mutableListOf()
        val parent: Node = p.parent!!
        parent.childEdges.forEach {
            if (it.target == p) {
                list.add(it)
            }
        }
        val gparent:Node = p.parent.parent!!
        gparent.childEdges.forEach {
            if (it.target == p) {
                list.add(it)
            }
        }
        return list
    }
}