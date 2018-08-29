use futures::Stream;
use futures::Poll;
use futures::Async;

use ::structs::*;
use ::EveError;

pub struct Source {
    // struct members can be added here
}

impl Source {
    pub fn new() -> Self {
        Source {
            // initialization of struct members
        }
    }
}

impl Stream for Source {
    type Item = Coordinate;    // use struct as defined in structs.rs
    type Error = EveError;

    // this function will be called to request new event data from a source
    fn poll(&mut self) -> Poll<Option<Self::Item>, Self::Error> {
        Ok(Async::Ready(Some(Coordinate {
            // initialization of struct members
             x: 0,
             y: 0
        })))
    }
}
