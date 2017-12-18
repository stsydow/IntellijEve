package codegen

import editor.*
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
use std::fmt;

pub mod evethread;

/// An OutgoingPort is a port capable of sending data to [IncomingPorts](struct.IncomingPort.html)
///
/// The OutgoingPort owns a [SuccessorInstanceList](struct.SuccessorInstanceList.html) for every successor node, of whom each will receive the data sent through this port.
#[derive(Debug)]
pub struct OutgoingPort<T> {
    pub successors: Vec<SuccessorInstanceList<T>>,
    /// TODO move this to the instance lists
    pub idx: usize
}

/// A SuccessorInstanceList is a container of senders to instances of the same successor
///
/// The SuccessorInstanceList owns a sender for every instance of a particular successor. Outgoing data is sent to one of them.
pub struct SuccessorInstanceList<T> {
    pub senders: Vec<Sender<T>>,
    pub filter: Vec<Box<Fn(&T) -> bool + Send + Sync>>
}

impl<T> fmt::Debug for SuccessorInstanceList<T> {
    fn fmt(&self, f: &mut fmt::Formatter) -> fmt::Result {
        write!(f, "SuccessorInstanceList")
    }
}

/// Sends data to one instance of every successor node
///
/// # Arguments
/// * `t` - The data to be sent.
impl <T: Clone + fmt::Debug> OutgoingPort<T> {
    #[allow(dead_code)]
    pub fn send(&mut self, t: T) {
        let ref targets_vector = self.successors;
        match targets_vector.len() {
            0 => {
                panic!();
            },
            1 => {
                let ref target = targets_vector[0];
                let mut filter: bool  = true;
                for f in &target.filter {
                    filter &= f(&t);
                }
                if filter {
                    self.idx %= target.senders.len();
                    target.senders[self.idx].send(t.clone()).unwrap();
                    self.idx += 1;
                }
            },
            _ => {
                for target in targets_vector {
                    let mut filter: bool  = true;
                    for f in &target.filter {
                        filter &= f(&t);
                    }
                    if filter {
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
}

/// An IncomingPort is a port capable of receiving data sent from [OutgoingPorts](struct.OutgoingPort.html)
///
/// The IncomingPort of an instance owns the respective receiver of senders belonging to every instance of every predecessor node.
#[derive(Debug)]
pub struct IncomingPort<T> {
    pub receiver: Receiver<T>,
    pub dummy_sender: Sender<T>
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
use std::thread::JoinHandle;

use eveamcp::evethread::EveThread;
use nodes::Graph;
use nodes::NormalNodeSet;
use nodes::SourceNodeSet;
use nodes::NormalNode;
use nodes::SourceNode;

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
""");
        builder.append("""
fn build_initial_instances(graph: &Arc<RwLock<Graph>>) -> (Vec<Arc<Mutex<SourceNode>>>, Vec<Arc<Mutex<NormalNode>>>) {
    let mut source_nodes: Vec<Arc<Mutex<SourceNode>>> = vec!();
    let mut normal_nodes: Vec<Arc<Mutex<NormalNode>>> = vec!();
""");
        graphs.forEach {
            if (it.childNodes.count() == 0) {
                if (isSourceNode(it)) {
                    builder.append("""
    source_nodes.push(::nodes::construct_new_${it.id}_instance(&graph));""")
                } else {
                    builder.append("""
    normal_nodes.push(::nodes::construct_new_${it.id}_instance(&graph));""")
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
use std::fmt::Debug;
use std::sync::Arc;
use std::sync::Mutex;
use std::sync::mpsc::TryRecvError;
use std::sync::RwLock;
use std::sync::mpsc::Sender;
use std::sync::mpsc::Receiver;
use std::sync::mpsc::channel;

use eveamcp::OutgoingPort;
use eveamcp::IncomingPort;
use eveamcp::SuccessorInstanceList;""");
        graphs.forEach {
            if (it.childNodes.count() == 0) {
                builder.append("""
use nodes::node_${it.name.toLowerCase()}::${it.name}InstanceStorage;""");
            }
        }
        builder.append("""
#[allow(unused_imports)] use structs::*;

pub trait Node: Send + Debug {
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
    fn tick(&mut self) -> bool;
}
pub trait NormalNode: NormalNodeInternal {
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
""");
        graphs.forEach {
            val graph = it
            if(graph.childNodes.count() == 0) {
                if(isSourceNode(graph)) {
                    var i = 0
                    graph.out_ports.forEach {
                        val port = it
                        val successorConnections = getConnectionsToSuccessors(port)
                        val successorDisctinctConnections = successorConnections.distinctBy{ it.destination.parent!!}
                        builder.append("""
#[derive(Debug)]
pub struct ${graph.name}OutPort$i {""")
                        successorDisctinctConnections.forEach {
                            builder.append("""
    pub ${it.destination.id}: OutgoingPort<${it.destination.message_type}>,""")
                        }
                        builder.append("""
}

impl ${graph.name}OutPort$i {
    pub fn send(&mut self, t: ${it.message_type}) {
        """)
                        successorDisctinctConnections.forEach {
                            builder.append("""
        self.${it.destination.id}.send(t.clone());""")
                        }
                        builder.append("""
    }
}""")
                        i+=1
                    }
                    builder.append(
"""

#[derive(Debug)]
pub struct ${it.name}Instance {
    pub instance_id: u64,""");
                    i=0
                    it.out_ports.forEach {
                        builder.append("""
    pub port_${it.id}: ${graph.name}OutPort$i,""");
                        i+=1
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
                    var i = 0
                    graph.out_ports.forEach {
                        val port = it
                        val successorConnections = getConnectionsToSuccessors(port)
                        val successorNodes = successorConnections.distinctBy{ it.destination.parent!!}
                        builder.append("""
#[derive(Debug)]
pub struct ${graph.name}OutPort$i {""")
                        successorNodes.forEach {
                            builder.append("""
    pub ${it.destination.id}: OutgoingPort<${it.destination.message_type}>,
""")
                        }
                        i+=1
                    }
                    builder.append(
                            """
#[derive(Debug)]
pub struct ${it.name}Instance {
    pub instance_id: u64,""");
                    i = 0
                    it.out_ports.forEach {
                        builder.append("""
    pub port_${it.id}: ${graph.name}OutPort$i,""");
                        i+=1
                    }
                    if (!isSourceNode(it)) {
                        builder.append("""
    pub incoming_port: IncomingPort<${it.in_port.message_type}>,""");
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
    fn tick(&mut self) -> bool {
        match self.incoming_port.try_receive() {
            Ok(e) => {
                self.handle_event(e);
                true
            },
            Err(TryRecvError::Empty) => true,
            Err(TryRecvError::Disconnected) => false
        }
    }
}
""");
                }

            }
        }

        graphs.forEach {
            if (it.childNodes.count() == 0) {
                val node = it;
                val msgType = node.in_port.message_type
                val predecessorConnections = getConnectionsToPredecessors(it.in_port)
                val predecessorNodesDisctinct = predecessorConnections.map { it.sourcePort.parent!! }.distinct()
                val successorConnections = mutableListOf<EveConnection>()
                node.out_ports.forEach {
                    successorConnections.addAll(getConnectionsToSuccessors(it))
                }
                val successorNodesDisctinct = successorConnections.map { it.destination.parent!! }.distinct()
                builder.append("""
#[allow(dead_code)]
pub fn construct_new_${it.id}_instance(graph: &Arc<RwLock<Graph>>) -> Arc<Mutex<${it.name}Instance>> {
    // lock predecessor, our, and successor sets
    let graph: &Graph = &*graph.read().unwrap();""")
                predecessorNodesDisctinct.forEach {
                    builder.append("""
    let ${it.id}_set = &mut *graph.${it.id}.lock().unwrap();""")
                }

                builder.append("""
    let ${node.id}_set = &mut *graph.${node.id}.lock().unwrap();""")
                successorNodesDisctinct.forEach {
                    builder.append("""
    let ${it.id}_set = &mut *graph.${it.id}.lock().unwrap();""")
                }
                builder.append("""

    // create new instance
    ${node.id}_set.idx += 1;""")
                if (!isSourceNode(node)) {
                    builder.append("""
    let (${node.id}_sender, ${node.id}_receiver): (Sender<$msgType>, Receiver<$msgType>) = channel();""")
                }
                builder.append("""
    let ${node.id}_instance = Arc::new(Mutex::new(${node.name}Instance {
        instance_id: ${node.id}_set.idx,""")
                if (!isSourceNode(node)) {
                    builder.append("""
        incoming_port: IncomingPort {
            receiver: ${node.id}_receiver,
            dummy_sender: ${node.id}_sender.clone()
        },""")
                }
                var i = 0
                node.out_ports.forEach {
                    builder.append("""
        port_${it.id}: ${node.name}OutPort$i {""")
                    val connections = getConnectionsToSuccessors(it)
                    val destinations = connections.map { it.destination }.distinct()
                    destinations.forEach {
                        val destination = it
                        val destinationConnections = connections.filter { it.destination == destination }
                        builder.append("""
            ${destination.id}: OutgoingPort {
                successors: vec!(""")
                        destinationConnections.forEach {
                            builder.append("""
                    SuccessorInstanceList {
                        senders: vec!(),
                        filter: vec!(""")
                            it.filters.forEach {
                                builder.append("""
                            Box::new(${it}_filter),""")
                            }
                            builder.append("""
                        )
                    },""")
                        }
                        builder.append("""),
                idx: 0""")
                        builder.append("""
            },""")
                    }
                    builder.append("""
        },""")
                    i+=1
                }
                builder.append("""
        instance_storage: None
    }));
    ${node.id}_set.instances.insert(${node.id}_set.idx, ${node.id}_instance.clone());
""");
                if (predecessorNodesDisctinct.size > 0) {
                    builder.append("""
    // add sender to predecessors""")
                    predecessorNodesDisctinct.forEach {
                        val predecessorNode = it
                        val predecessorNodeConnections = predecessorConnections.filter { it.sourcePort.parent!! == predecessorNode }
                        builder.append("""
    for (_instance_id, instance_mutex) in &${predecessorNode.id}_set.instances {
        let instance: &mut SourceNode = &mut *instance_mutex.lock().unwrap();
        let instance: &mut ${predecessorNode.name}Instance = instance.as_any().downcast_mut::<${predecessorNode.name}Instance>().unwrap();""")
                        predecessorNodeConnections.forEach {
                            val connection = it
                            builder.append("""
        for successor_list in &mut instance.port_${connection.sourcePort.id}.${connection.destination.id}.successors {
            successor_list.senders.push(${node.id}_sender.clone())
        }""")
                        }
                        builder.append("""
    }""")
                    }
                }
                if (node.out_ports.size > 0) {
                    builder.append("""
    // fetch senders from successors
    {""")
                    builder.append("""
        let ${node.id}_instance = &mut *${node.id}_instance.lock().unwrap();""")
                    node.out_ports.forEach {
                        val port = it
                        val connections = getConnectionsToSuccessors(port)
                        val destinations = connections.map { it.destination }.distinct()
                        destinations.forEach {
                            val destination = it
                            val destinationParent = it.parent!!
                            val connectionsToDestination = connections.filter {it.destination == destination}
                            builder.append("""
        for successor_instance_list in &mut ${node.id}_instance.port_${port.id}.${destination.id}.successors {""")
                            connectionsToDestination.forEach {
                                builder.append("""
             for (_${destinationParent.id}, ${destinationParent.id}_mutex) in &${destinationParent.id}_set.instances {
                let instance: &mut NormalNode = &mut *${destinationParent.id}_mutex.lock().unwrap();
                let instance: &mut ${destinationParent.name}Instance = instance.as_any().downcast_mut::<${destinationParent.name}Instance>().unwrap();
                successor_instance_list.senders.push(instance.incoming_port.dummy_sender.clone())
             }""")
                            }
                            builder.append("""
        }""")
                        }
                        /*
                        builder.append("""
        for successor_instance_list in &mut ${node.id}_instance.port_${port.id}.${connections[0].destination.id}.successors {""")
                        connections.forEach {
                            val connection = it
                            builder.append("""
            for (_${connection.destination.parent!!.id}_id, ${connection.destination.parent.id}_mutex) in &${connection.destination.parent.id}_set.instances {

            }""")
                        }
                        */
                    }
                    builder.append("""
    }""")
                }

                builder.append("""
    ${node.id}_instance
}
""")
            }
        }
        graphs.forEach {
            val filter = it.getProperty(PropertyType.Filter)
            if (filter != null) {
                builder.append("""
fn ${it.id}_filter(e: &${it.in_port.message_type}) -> bool {
    e.${filter}
}
""")
            }
        }
        return builder.toString();
    }

    fun getEvethreadRs(rootGraph: RootNode, graphs: Collection<Node>): String {
        val builder = StringBuilder();
        builder.append(
                """use std::sync::Arc;
use std::sync::Mutex;
use std::sync::RwLock;
use std::thread;
use std::thread::JoinHandle;

use nodes::Graph;
use nodes::NormalNode;
use nodes::SourceNode;

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
            for node in self.normal_nodes.as_mut_slice() {
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
        let mut node = self.normal_nodes[i].lock().unwrap();
        if !node.tick() {
            let i_id = node.get_instance_id();
            let m_id = node.get_model_id();
            remove_instance(i_id, m_id, g);
            false
        } else {
            true
        }
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

    fun getConnectionsToPredecessors(p: Port): List<EveConnection> {
        return getConnections(p, p,true)
    }

    fun getConnectionsToSuccessors(p: Port): List<EveConnection> {
        return getConnections(p, p,false)
    }

    fun getConnections(p: Port, destination: Port, traverseWest: Boolean): List<EveConnection> {
        val l = mutableListOf<EveConnection>()
        val inc = getIncomingEdges(p)
        if (traverseWest) {
            if (p.direction == Direction.OUT) {
                if (inc.size > 0) {
                    inc.forEach {
                        val c = getConnections(it.source, destination, traverseWest)
                        l.addAll(c)
                    }
                } else {
                    l.add(EveConnection(p, destination, mutableListOf()))
                }
            } else {
                inc.forEach {
                    l.addAll(getConnections(it.source, destination, traverseWest))
                }
            }
        } else {
            val out = getOutgoingEdges(p)
            if (out.size > 0) {
                out.forEach {
                    val c = getConnections(it.target, destination, traverseWest)
                    val filter = p.parent!!.getProperty(PropertyType.Filter)
                    if (filter != null) {
                        c.forEach {
                            it.filters.add(p.parent.id)
                        }
                    }
                    l.addAll(c)
                }
            } else {
                if (p.direction == Direction.IN) {
                    val filter = p.parent!!.getProperty(PropertyType.Filter)
                    if (filter != null) {
                        l.add(EveConnection(destination, p, mutableListOf(p.parent.id)))
                    } else {
                        l.add(EveConnection(destination, p, mutableListOf()))
                    }
                }
            }
        }
        return l
    }
}

class EveConnection(val sourcePort: Port, val destination: Port, val filters: MutableList<String>)