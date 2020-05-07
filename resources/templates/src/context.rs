use futures::{Async, Poll, Stream};

use crate::structs::EveError;

pub trait Context {
    type Event;
    type Result;

    fn work(&mut self, event: Self::Event) -> Self::Result;
    /*fn new() -> Self;*/
}

pub struct GlobalContext<Ctx, InStream> {
    context: Ctx,
    input: InStream,
}

impl<E, R, Ctx, InStream> GlobalContext<Ctx, InStream>
    where Ctx: Context<Event=E, Result=R>, InStream: Stream
{
    pub fn new(input: InStream, initial_ctx: Ctx) -> Self {
        GlobalContext {
            context: initial_ctx,
            input,
        }
    }
}

/*
pub fn stream_context<E, R, InStream, Ctx> (input:InStream, initial_ctx: Ctx) -> GlobalContext<Ctx, InStream>
    where Ctx:Context<Event=E, Result=R>,
          InStream: Stream<Item=E, Error=EveError>
{
    GlobalContext::new(input, initial_ctx)
}
*/

impl<E, R, Ctx: Context<Event=E, Result=R>, InStream: Stream<Item=E, Error=EveError>> Stream for GlobalContext<Ctx, InStream> {
    type Item = R;
    type Error = EveError;

    fn poll(&mut self) -> Poll<Option<Self::Item>, Self::Error> {
        let async_event = self.input.poll()?;
        let result = match async_event {
            Async::Ready(Some(event)) => {
                let result = self.context.work(event);
                Async::Ready(Some(result))
            }
            Async::Ready(None) => Async::Ready(None),
            Async::NotReady => Async::NotReady
        };

        Ok(result)
    }
}