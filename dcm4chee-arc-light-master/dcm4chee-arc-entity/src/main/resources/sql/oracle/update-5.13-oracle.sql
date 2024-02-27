-- can be applied on running archive 5.12
alter table queue_msg add batchID varchar2(255 char);
create index UK_2rbj4jw6ffs0ytec06ebv5nld on queue_msg (batchID);
create table diff_task (pk number(19,0) not null, check_different number(1,0) not null, check_missing number(1,0) not null, compare_fields varchar2(255 char), created_time timestamp not null, different number(10,0) not null, local_aet varchar2(255 char) not null, matches number(10,0) not null, missing number(10,0) not null, primary_aet varchar2(255 char) not null, query_str varchar2(255 char) not null, secondary_aet varchar2(255 char) not null, updated_time timestamp not null, queue_msg_fk number(19,0) not null, primary key (pk));
create table diff_task_attrs (dicomattrs_fk number(19,0) not null, diff_task_fk number(19,0) not null, primary key (dicomattrs_fk));
alter table diff_task add constraint UK_dlrthwe594xvfeta3kyuih3ip  unique (queue_msg_fk);
create index UK_ji31t9tjovks2hi8c220kvh2o on diff_task (local_aet);
create index UK_kigvmtftfxwb5ekb5a1d48okh on diff_task (primary_aet);
create index UK_4esq3qn81u1ww82vsheeiy424 on diff_task (secondary_aet);
create index UK_swm85lyyxy8sr2oot4dcrjck4 on diff_task (created_time);
create index UK_dyakfsqdbpk1bgme5i9xw20jj on diff_task (updated_time);
create index UK_1b3rghtxfl0byme5urnxmd4xl on diff_task (check_missing);
create index UK_ex8bacv78us242731mpyrigpd on diff_task (check_different);
create index UK_d92i2fx5i138fbehp4qfk on diff_task (compare_fields);
alter table diff_task add constraint FK_dlrthwe594xvfeta3kyuih3ip foreign key (queue_msg_fk) references queue_msg;
alter table diff_task_attrs add constraint FK_1sbjtesix8hkq310i84lfg0xi foreign key (diff_task_fk) references diff_task;
alter table diff_task_attrs add constraint FK_72rsx022j2wu9noi6jldvq95r foreign key (dicomattrs_fk) references dicomattrs;
create sequence diff_task_pk_seq;
create index FK_1sbjtesix8hkq310i84lfg0xi on diff_task_attrs (diff_task_fk) ;
