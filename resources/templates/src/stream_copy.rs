extern crate futures;
extern crate tokio_core;

use futures::Poll;
use futures::Stream;
use futures::Async;

use std::sync::Arc;
use std::sync::Mutex;
use std::clone::Clone;
use std::sync::MutexGuard;

use ::EveError;

pub struct StreamCopy<T, S: Stream<Item=T, Error=EveError>> {
    input: S,
    buffers: Vec<Vec<T>>,
    idx: usize
}

pub struct StreamCopyMutex<T, S: Stream<Item=T, Error=EveError>>(Arc<Mutex<StreamCopy<T, S>>>);

pub struct StreamCopyOutPort<T, S: Stream<Item=T, Error=EveError>> {
    id: usize,
    source: StreamCopyMutex<T, S>
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
                                    },
                                    None => Ok(Async::Ready(None))
                                }
                            },
                            Async::NotReady => Ok(Async::NotReady)
                        }
                    }
                    Err(e) => Err(e)
                }
            }
        }
    }

    fn buffered_poll(&mut self, id: usize) -> Option<T>{
        let buffer = &mut self.buffers[id];
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
        StreamCopyMutex(self.0.clone())
    }
}

impl<T: Clone, S: Stream<Item=T, Error=EveError>> StreamCopyMutex<T, S> {

    pub fn new(input:S) -> StreamCopyMutex<T, S> {
        StreamCopyMutex(
            Arc::new(Mutex::new(StreamCopy {
                input, buffers: vec!(), idx: 0
            })))
    }

    pub fn lock(&self) -> MutexGuard<StreamCopy<T, S>> {
        self.0.lock().unwrap()
    }

    pub fn create_output_locked(&self) -> StreamCopyOutPort<T, S> {
        let mut inner = self.lock();
        let val = StreamCopyOutPort {
            source: (*self).clone(),
            id: inner.idx
        };
        inner.buffers.push(vec!());
        inner.idx += 1;
        val
    }

    pub fn poll_locked(&self, id: usize) -> Poll<Option<T>, EveError> {
        let mut inner = self.lock();
        inner.poll(id)
    }
}