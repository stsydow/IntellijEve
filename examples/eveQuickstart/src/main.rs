extern crate futures;
extern crate tokio_core;

use futures::Async;
use futures::Future;
use futures::future::ok;
use futures::Poll;
use futures::Stream;
use nodes::sink;
use nodes::source;
use std::clone::Clone;
use std::sync::Arc;
use std::sync::Mutex;
use std::sync::MutexGuard;
use structs::*;
use tokio_core::reactor::Core;

mod nodes;
mod structs;

#[derive(Debug)]
pub enum EveError {
    UnknownError
}

pub struct StreamCopy<T, S: Stream<Item=T, Error=EveError>> {
    input: S,
    buffers: Vec<Vec<T>>,
    idx: usize,
}

struct StreamCopyMutex<T, S: Stream<Item=T, Error=EveError>> {
    inner: Arc<Mutex<StreamCopy<T, S>>>
}

struct StreamCopyOutPort<T, S: Stream<Item=T, Error=EveError>> {
    id: usize,
    source: StreamCopyMutex<T, S>,
}

impl<T: Clone, S: Stream<Item=T, Error=EveError>> StreamCopy<T, S> {
    fn poll(&mut self, id: usize) -> Poll<Option<T>, EveError> {
        let buffered = self.buffered_poll(id);
        match buffered {
            Some(buffered) => {
                Ok(Async::Ready(Some(buffered)))
            }
            None => {
                let result = self.input.poll();
                match result {
                    Ok(async) => {
                        match async {
                            Async::Ready(ready) => {
                                match ready {
                                    Some(event) => {
                                        for mut buffer in &mut self.buffers {
                                            buffer.push(event.clone())
                                        }
                                        self.poll(id)
                                    }
                                    None => Ok(Async::Ready(None))
                                }
                            }
                            Async::NotReady => Ok(Async::NotReady)
                        }
                    }
                    Err(e) => Err(e)
                }
            }
        }
    }

    fn buffered_poll(&mut self, id: usize) -> Option<T> {
        let mut buffer = &mut self.buffers[id];
        if buffer.len() > 0 {
            Some(buffer.remove(0))
        } else {
            None
        }
    }
}

impl<T: Clone, S: Stream<Item=T, Error=EveError>> Stream for StreamCopyOutPort<T, S> {
    type Item = T;
    type Error = EveError;

    fn poll(&mut self) -> Poll<Option<Self::Item>, Self::Error> {
        self.source.poll_locked(self.id)
    }
}

impl<T, S: Stream<Item=T, Error=EveError>> Clone for StreamCopyMutex<T, S> {
    fn clone(&self) -> StreamCopyMutex<T, S> {
        StreamCopyMutex {
            inner: self.inner.clone()
        }
    }
}

impl<T: Clone, S: Stream<Item=T, Error=EveError>> StreamCopyMutex<T, S> {
    fn lock(&self) -> MutexGuard<StreamCopy<T, S>> {
        self.inner.lock().unwrap()
    }

    fn create_output_locked(&self) -> StreamCopyOutPort<T, S> {
        let mut inner = self.lock();
        let val = StreamCopyOutPort {
            source: (*self).clone(),
            id: inner.idx,
        };
        inner.buffers.push(vec!());
        inner.idx += 1;
        val
    }

    fn poll_locked(&self, id: usize) -> Poll<Option<T>, EveError> {
        let mut inner = self.lock();
        inner.poll(id)
    }
}

pub fn main() {
    let mut core = Core::new().unwrap();
    core.run(build()).unwrap();
}


pub fn pipeline_uielement4_uielement6() -> impl Stream<Item=(), Error=EveError> {
    source::Source::new()
        .map(|event| {
            sink::tick(event)
        })
}

pub fn build() -> impl Future<Item=(), Error=EveError> {
    let pipeline_uielement4_uielement6 = pipeline_uielement4_uielement6();

    pipeline_uielement4_uielement6
        .for_each(|_| ok(()))
}

